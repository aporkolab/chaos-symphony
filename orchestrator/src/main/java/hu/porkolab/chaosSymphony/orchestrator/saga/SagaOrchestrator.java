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

            String reason = "Retry compensation attempt " + saga.getRetryCount();
            
            
            if (saga.getFailureReason() != null && saga.getFailureReason().contains("shipping")) {
                onShippingFailed(saga.getOrderId(), reason);
            } else if (saga.getFailureReason() != null && saga.getFailureReason().contains("inventory")) {
                onInventoryFailed(saga.getOrderId(), reason);
            } else {
                onPaymentFailed(saga.getOrderId(), reason);
            }
        }
    }
}
