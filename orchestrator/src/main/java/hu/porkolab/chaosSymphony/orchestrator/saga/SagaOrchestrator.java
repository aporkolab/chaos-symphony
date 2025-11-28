package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import hu.porkolab.chaosSymphony.orchestrator.kafka.OrderStatusProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class SagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final CompensationProducer compensationProducer;
    private final OrderStatusProducer orderStatusProducer;
    private final Counter compensationsTriggered;
    private final Counter compensationsCompleted;
    private final Timer processingTimeTimer;
    private final Counter dltMessagesTotal;
    private final Counter ordersStartedCounter;
    private final Counter ordersFailedCounter;

    @Autowired
    public SagaOrchestrator(SagaRepository sagaRepository,
                            CompensationProducer compensationProducer,
                            @Autowired(required = false) OrderStatusProducer orderStatusProducer,
                            @Qualifier("compensationsTriggered") Counter compensationsTriggered,
                            @Qualifier("compensationsCompleted") Counter compensationsCompleted,
                            @Qualifier("processingTimeTimer") Timer processingTimeTimer,
                            @Qualifier("dltMessagesTotal") Counter dltMessagesTotal,
                            @Qualifier("ordersStarted") Counter ordersStartedCounter,
                            @Qualifier("ordersFailed") Counter ordersFailedCounter) {
        this.sagaRepository = sagaRepository;
        this.compensationProducer = compensationProducer;
        this.orderStatusProducer = orderStatusProducer;
        this.compensationsTriggered = compensationsTriggered;
        this.compensationsCompleted = compensationsCompleted;
        this.processingTimeTimer = processingTimeTimer;
        this.dltMessagesTotal = dltMessagesTotal;
        this.ordersStartedCounter = ordersStartedCounter;
        this.ordersFailedCounter = ordersFailedCounter;
    }

    
    public SagaOrchestrator(SagaRepository sagaRepository,
                            CompensationProducer compensationProducer,
                            MeterRegistry meterRegistry) {
        this(sagaRepository, compensationProducer, null,
             meterRegistry.counter("saga.compensations.triggered"),
             meterRegistry.counter("saga.compensations.completed"),
             Timer.builder("processing_time_ms").register(meterRegistry),
             meterRegistry.counter("dlt_messages_total"),
             meterRegistry.counter("orders.started"),
             meterRegistry.counter("orders.failed"));
    }

    @Transactional
    public SagaInstance startSaga(String orderId) {
        
        var existing = sagaRepository.findById(orderId);
        if (existing.isPresent()) {
            log.debug("Saga already exists for orderId={}, returning existing", orderId);
            return existing.get();
        }
        
        log.info("Starting saga for orderId={}", orderId);
        ordersStartedCounter.increment();
        SagaInstance saga = SagaInstance.builder()
            .orderId(orderId)
            .state(SagaState.STARTED)
            .retryCount(0)
            .build();
        return sagaRepository.save(saga);
    }

    
    @Transactional
    public SagaInstance startSagaAndRequestPayment(String orderId) {
        return startSagaAndRequestPayment(orderId, null);
    }

    
    @Transactional
    public SagaInstance startSagaAndRequestPayment(String orderId, String shippingAddress) {
        
        var existing = sagaRepository.findById(orderId);
        if (existing.isPresent()) {
            log.debug("Saga already exists for orderId={}, returning existing", orderId);
            return existing.get();
        }
        
        log.info("Starting saga and requesting payment for orderId={}, address={}", orderId, shippingAddress);
        ordersStartedCounter.increment();
        SagaInstance saga = SagaInstance.builder()
            .orderId(orderId)
            .state(SagaState.PAYMENT_PENDING)
            .shippingAddress(shippingAddress)
            .retryCount(0)
            .build();
        return sagaRepository.save(saga);
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
    public void onInventoryRequested(String orderId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
            saga -> {
                saga.transitionTo(SagaState.INVENTORY_PENDING);
                sagaRepository.save(saga);
                log.info("Saga {} transitioned to INVENTORY_PENDING", orderId);
            },
            () -> log.warn("Saga not found for orderId={} on inventory request", orderId)
        );
    }

    @Transactional
    public void onPaymentFailed(String orderId, String reason) {
        sagaRepository.findById(orderId).ifPresentOrElse(
            saga -> {
                saga.fail(SagaState.PAYMENT_FAILED, reason);
                saga.transitionTo(SagaState.COMPENSATING);
                sagaRepository.save(saga);
                ordersFailedCounter.increment();
                log.warn("Saga {} PAYMENT_FAILED: {}", orderId, reason);
                
                
                compensationProducer.requestOrderCancellation(orderId, reason);
                compensationsTriggered.increment();
                sendStatusUpdate(orderId, "PAYMENT_FAILED", reason);
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
    public void onShippingRequested(String orderId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
            saga -> {
                saga.transitionTo(SagaState.SHIPPING_PENDING);
                sagaRepository.save(saga);
                log.info("Saga {} transitioned to SHIPPING_PENDING", orderId);
            },
            () -> log.warn("Saga not found for orderId={} on shipping request", orderId)
        );
    }

    
    @Transactional(readOnly = true)
    public String getShippingAddress(String orderId) {
        return sagaRepository.findById(orderId)
            .map(SagaInstance::getShippingAddress)
            .orElse(null);
    }

    @Transactional
    public void onInventoryFailed(String orderId, String reason) {
        sagaRepository.findById(orderId).ifPresentOrElse(
            saga -> {
                saga.fail(SagaState.INVENTORY_FAILED, reason);
                saga.transitionTo(SagaState.COMPENSATING);
                sagaRepository.save(saga);
                ordersFailedCounter.increment();
                
                if (saga.getPaymentId() != null) {
                    compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                }
                compensationProducer.requestOrderCancellation(orderId, reason);
                compensationsTriggered.increment();
                log.warn("Saga {} INVENTORY_FAILED, triggering compensation: {}", orderId, reason);
                sendStatusUpdate(orderId, "INVENTORY_FAILED", reason);
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
                
                
                if (saga.getCreatedAt() != null) {
                    Duration processingDuration = Duration.between(saga.getCreatedAt(), Instant.now());
                    processingTimeTimer.record(processingDuration);
                    log.info("Saga {} COMPLETED in {}ms", orderId, processingDuration.toMillis());
                } else {
                    log.info("Saga {} COMPLETED successfully", orderId);
                }
                sendStatusUpdate(orderId, "COMPLETED", null);
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
                ordersFailedCounter.increment();

                if (saga.getInventoryReservationId() != null) {
                    compensationProducer.requestInventoryRelease(
                            orderId, saga.getInventoryReservationId(), reason);
                }
                if (saga.getPaymentId() != null) {
                    compensationProducer.requestPaymentRefund(orderId, saga.getPaymentId(), reason);
                }
                compensationProducer.requestOrderCancellation(orderId, reason);
                compensationsTriggered.increment();
                log.warn("Saga {} SHIPPING_FAILED, triggering compensation: {}", orderId, reason);
                sendStatusUpdate(orderId, "SHIPPING_FAILED", reason);
            },
            () -> log.warn("Saga not found for orderId={} on shipping failure", orderId)
        );
    }

    @Transactional
    public void onCompensationCompleted(String orderId) {
        markCompensated(orderId);
    }

    @Transactional
    public void markCompensated(String orderId) {
        sagaRepository.findById(orderId).ifPresentOrElse(
            saga -> {
                saga.transitionTo(SagaState.COMPENSATED);
                sagaRepository.save(saga);
                compensationsCompleted.increment();
                log.info("Saga {} fully COMPENSATED", orderId);
                sendStatusUpdate(orderId, "CANCELLED", "Order cancelled after compensation");
            },
            () -> log.warn("Saga not found for orderId={} on compensation completion", orderId)
        );
    }

    @Transactional
    public void retryStuckCompensations() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<SagaState> compensatingStates = List.of(SagaState.COMPENSATING);
        
        List<SagaInstance> stuckSagas = sagaRepository.findStuckSagas(compensatingStates, threshold);
        
        for (SagaInstance saga : stuckSagas) {
            log.info("Retrying stuck compensation for saga {}", saga.getOrderId());
            saga.setRetryCount(saga.getRetryCount() + 1);
            sagaRepository.save(saga);
            
            String reason = "Retry compensation attempt #" + saga.getRetryCount();
            
            if (saga.getInventoryReservationId() != null) {
                compensationProducer.requestInventoryRelease(
                    saga.getOrderId(), saga.getInventoryReservationId(), reason);
            }
            if (saga.getPaymentId() != null) {
                compensationProducer.requestPaymentRefund(
                    saga.getOrderId(), saga.getPaymentId(), reason);
            }
            compensationProducer.requestOrderCancellation(saga.getOrderId(), reason);
        }
    }

    
    @Transactional
    public void handleStuckPendingSagas() {
        Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<SagaState> pendingStates = List.of(
            SagaState.PAYMENT_PENDING,
            SagaState.INVENTORY_PENDING,
            SagaState.SHIPPING_PENDING
        );
        
        List<SagaInstance> stuckSagas = sagaRepository.findStuckSagas(pendingStates, threshold);
        
        for (SagaInstance saga : stuckSagas) {
            String orderId = saga.getOrderId();
            SagaState currentState = saga.getState();
            
            log.warn("Saga {} stuck in {} for too long, triggering timeout failure", orderId, currentState);
            
            String reason = "Timeout waiting for " + currentState.name() + " response";
            
            switch (currentState) {
                case PAYMENT_PENDING -> onPaymentFailed(orderId, reason);
                case INVENTORY_PENDING -> onInventoryFailed(orderId, reason);
                case SHIPPING_PENDING -> onShippingFailed(orderId, reason);
                default -> log.warn("Unexpected pending state {} for saga {}", currentState, orderId);
            }
        }
    }

    private void sendStatusUpdate(String orderId, String status, String reason) {
        if (orderStatusProducer != null) {
            orderStatusProducer.sendStatusUpdate(orderId, status, reason);
        }
    }

    
    public void recordDltMessage() {
        dltMessagesTotal.increment();
        ordersFailedCounter.increment();
        log.warn("DLT message recorded - dltCount incremented, ordersFailed incremented");
    }
}
