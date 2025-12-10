package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
public class SagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final CompensationProducer compensationProducer;
    private final Counter compensationsTriggered;
    private final Counter compensationsCompleted;

    public SagaOrchestrator(
            SagaRepository sagaRepository,
            CompensationProducer compensationProducer,
            MeterRegistry meterRegistry) {
        this.sagaRepository = sagaRepository;
        this.compensationProducer = compensationProducer;
        this.compensationsTriggered = meterRegistry.counter("saga.compensations.triggered");
        this.compensationsCompleted = meterRegistry.counter("saga.compensations.completed");
    }

    
    @Transactional
    public SagaInstance startSaga(String orderId) {
        log.info("Starting saga for orderId={}", orderId);

        SagaInstance saga = SagaInstance.builder()
                .orderId(orderId)
                .state(SagaState.STARTED)
                .build();

        return sagaRepository.save(saga);
    }

    
    public Optional<SagaInstance> getSaga(String orderId) {
        return sagaRepository.findById(orderId);
    }

    
    @Transactional
    public void onPaymentCompleted(String orderId, String paymentId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.setPaymentId(paymentId);
                    saga.transitionTo(SagaState.PAYMENT_COMPLETED);
                    sagaRepository.save(saga);
                    log.info("Saga {} transitioned to PAYMENT_COMPLETED", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on payment completion", orderId)
        );
    }

    
    @Transactional
    public void onPaymentFailed(String orderId, String reason) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.fail(SagaState.PAYMENT_FAILED, reason);
                    saga.transitionTo(SagaState.COMPENSATING);
                    sagaRepository.save(saga);

                    
                    compensationProducer.requestOrderCancellation(orderId, reason);
                    compensationsTriggered.increment();
                    log.warn("Saga {} payment failed, compensating", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on payment failure", orderId)
        );
    }

    
    @Transactional
    public void onInventoryReserved(String orderId, String reservationId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.setInventoryReservationId(reservationId);
                    saga.transitionTo(SagaState.INVENTORY_RESERVED);
                    sagaRepository.save(saga);
                    log.info("Saga {} transitioned to INVENTORY_RESERVED", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on inventory reservation", orderId)
        );
    }

    
    @Transactional
    public void onInventoryFailed(String orderId, String reason) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.fail(SagaState.INVENTORY_FAILED, reason);
                    saga.transitionTo(SagaState.COMPENSATING);
                    sagaRepository.save(saga);

                    
                    if (saga.getPaymentId() != null) {
                        compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                    }
                    compensationProducer.requestOrderCancellation(orderId, reason);
                    compensationsTriggered.increment();
                    log.warn("Saga {} inventory failed, compensating", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on inventory failure", orderId)
        );
    }

    
    @Transactional
    public void onShippingCompleted(String orderId, String shippingId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.setShippingId(shippingId);
                    saga.transitionTo(SagaState.COMPLETED);
                    sagaRepository.save(saga);
                    log.info("Saga {} COMPLETED successfully", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on shipping completion", orderId)
        );
    }

    
    @Transactional
    public void onShippingFailed(String orderId, String reason) {
        sagaRepository.findById(orderId).ifPresentOrElse(
                saga -> {
                    saga.fail(SagaState.SHIPPING_FAILED, reason);
                    saga.transitionTo(SagaState.COMPENSATING);
                    sagaRepository.save(saga);

                    
                    if (saga.getInventoryReservationId() != null) {
                        compensationProducer.requestInventoryRelease(
                                orderId, saga.getInventoryReservationId(), reason);
                    }
                    if (saga.getPaymentId() != null) {
                        compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                    }
                    compensationProducer.requestOrderCancellation(orderId, reason);
                    compensationsTriggered.increment();
                    log.warn("Saga {} shipping failed, full compensation triggered", orderId);
                },
                () -> log.warn("Saga not found for orderId={} on shipping failure", orderId)
        );
    }

    
    @Transactional
    public void markCompensated(String orderId) {
        sagaRepository.findById(orderId).ifPresent(saga -> {
            saga.transitionTo(SagaState.COMPENSATED);
            sagaRepository.save(saga);
            compensationsCompleted.increment();
            log.info("Saga {} fully compensated", orderId);
        });
    }

    
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryStuckCompensations() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<SagaState> stuckStates = List.of(SagaState.COMPENSATING);

        List<SagaInstance> stuckSagas = sagaRepository.findStuckSagas(stuckStates, cutoff);

        if (!stuckSagas.isEmpty()) {
            log.warn("Found {} stuck sagas, retrying compensation", stuckSagas.size());
        }

        for (SagaInstance saga : stuckSagas) {
            if (saga.getRetryCount() >= 3) {
                log.error("Saga {} exceeded max retries, manual intervention required", saga.getOrderId());
                continue;
            }

            saga.setRetryCount(saga.getRetryCount() + 1);
            sagaRepository.save(saga);

            
            SagaState.CompensationStrategy strategy = determineCompensationStrategy(saga);
            executeCompensation(saga, strategy);
        }
    }

    
    private SagaState.CompensationStrategy determineCompensationStrategy(SagaInstance saga) {
        if (saga.getFailedState() != null) {
            return saga.getFailedState().getCompensationStrategy();
        }

        
        if (saga.getShippingId() != null) {
            return SagaState.CompensationStrategy.FULL_COMPENSATION;
        } else if (saga.getInventoryReservationId() != null) {
            return SagaState.CompensationStrategy.REFUND_AND_CANCEL;
        } else if (saga.getPaymentId() != null) {
            return SagaState.CompensationStrategy.REFUND_AND_CANCEL;
        } else {
            return SagaState.CompensationStrategy.CANCEL_ORDER_ONLY;
        }
    }

    
    private void executeCompensation(SagaInstance saga, SagaState.CompensationStrategy strategy) {
        String orderId = saga.getOrderId();
        String reason = "Retry compensation attempt " + saga.getRetryCount();

        log.info("Executing {} compensation for saga {}", strategy, orderId);

        switch (strategy) {
            case FULL_COMPENSATION -> {
                if (saga.getInventoryReservationId() != null) {
                    compensationProducer.requestInventoryRelease(
                            orderId, saga.getInventoryReservationId(), reason);
                }
                if (saga.getPaymentId() != null) {
                    compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                }
                compensationProducer.requestOrderCancellation(orderId, reason);
            }
            case REFUND_AND_CANCEL -> {
                if (saga.getPaymentId() != null) {
                    compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                }
                compensationProducer.requestOrderCancellation(orderId, reason);
            }
            case CANCEL_ORDER_ONLY -> compensationProducer.requestOrderCancellation(orderId, reason);
            case NONE -> log.warn("No compensation strategy for saga {}", orderId);
        }

        compensationsTriggered.increment();
    }
}
