package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private SagaRepository sagaRepository;

    @Mock
    private CompensationProducer compensationProducer;

    private MeterRegistry meterRegistry;
    private SagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orchestrator = new SagaOrchestrator(sagaRepository, compensationProducer, meterRegistry);
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should start saga with STARTED state")
        void startSaga_shouldCreateSagaWithStartedState() {
            
            String orderId = UUID.randomUUID().toString();
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            SagaInstance saga = orchestrator.startSaga(orderId);

            
            assertThat(saga.getOrderId()).isEqualTo(orderId);
            assertThat(saga.getState()).isEqualTo(SagaState.STARTED);
            verify(sagaRepository).save(any(SagaInstance.class));
        }

        @Test
        @DisplayName("Should complete full saga lifecycle successfully")
        void fullSagaLifecycle_shouldCompleteSuccessfully() {
            
            String orderId = UUID.randomUUID().toString();
            String paymentId = UUID.randomUUID().toString();
            String reservationId = UUID.randomUUID().toString();
            String shippingId = UUID.randomUUID().toString();

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.STARTED)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.onPaymentCompleted(orderId, paymentId);
            assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_COMPLETED);
            assertThat(saga.getPaymentId()).isEqualTo(paymentId);

            
            orchestrator.onInventoryReserved(orderId, reservationId);
            assertThat(saga.getState()).isEqualTo(SagaState.INVENTORY_RESERVED);
            assertThat(saga.getInventoryReservationId()).isEqualTo(reservationId);

            
            orchestrator.onShippingCompleted(orderId, shippingId);
            assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
            assertThat(saga.getShippingId()).isEqualTo(shippingId);

            
            verifyNoInteractions(compensationProducer);
        }
    }

    @Nested
    @DisplayName("Compensation Flow Tests")
    class CompensationFlowTests {

        @Test
        @DisplayName("Payment failure should trigger order cancellation only")
        void paymentFailed_shouldTriggerOrderCancellation() {
            
            String orderId = UUID.randomUUID().toString();
            String reason = "Insufficient funds";

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.PAYMENT_PENDING)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.onPaymentFailed(orderId, reason);

            
            assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
            assertThat(saga.getFailureReason()).isEqualTo(reason);
            
            verify(compensationProducer).requestOrderCancellation(orderId, reason);
            verify(compensationProducer, never()).requestPaymentRefund(any(), any(), any());
            verify(compensationProducer, never()).requestInventoryRelease(any(), any(), any());
        }

        @Test
        @DisplayName("Inventory failure should trigger payment refund and order cancellation")
        void inventoryFailed_shouldTriggerPaymentRefundAndOrderCancellation() {
            
            String orderId = UUID.randomUUID().toString();
            String paymentId = UUID.randomUUID().toString();
            String reason = "Out of stock";

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.PAYMENT_COMPLETED)
                    .paymentId(paymentId)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.onInventoryFailed(orderId, reason);

            
            assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
            
            verify(compensationProducer).requestPaymentRefund(orderId, paymentId, reason);
            verify(compensationProducer).requestOrderCancellation(orderId, reason);
            verify(compensationProducer, never()).requestInventoryRelease(any(), any(), any());
        }

        @Test
        @DisplayName("Shipping failure should trigger full compensation chain")
        void shippingFailed_shouldTriggerFullCompensationChain() {
            
            String orderId = UUID.randomUUID().toString();
            String paymentId = UUID.randomUUID().toString();
            String reservationId = UUID.randomUUID().toString();
            String reason = "Delivery address unreachable";

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.INVENTORY_RESERVED)
                    .paymentId(paymentId)
                    .inventoryReservationId(reservationId)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.onShippingFailed(orderId, reason);

            
            assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
            
            verify(compensationProducer).requestInventoryRelease(orderId, reservationId, reason);
            verify(compensationProducer).requestPaymentRefund(orderId, paymentId, reason);
            verify(compensationProducer).requestOrderCancellation(orderId, reason);
        }

        @Test
        @DisplayName("Should mark saga as compensated after successful compensation")
        void markCompensated_shouldTransitionToCompensatedState() {
            
            String orderId = UUID.randomUUID().toString();

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.COMPENSATING)
                    .failureReason("Test failure")
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.markCompensated(orderId);

            
            assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);
            verify(sagaRepository).save(saga);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle missing saga gracefully")
        void missingSaga_shouldLogWarningAndNotThrow() {
            
            String orderId = UUID.randomUUID().toString();
            when(sagaRepository.findById(orderId)).thenReturn(Optional.empty());

            
            orchestrator.onPaymentCompleted(orderId, "payment-123");
            orchestrator.onInventoryReserved(orderId, "reservation-123");
            orchestrator.onShippingCompleted(orderId, "shipping-123");
            orchestrator.onPaymentFailed(orderId, "reason");
            orchestrator.onInventoryFailed(orderId, "reason");
            orchestrator.onShippingFailed(orderId, "reason");

            
            verifyNoInteractions(compensationProducer);
        }

        @Test
        @DisplayName("Should handle null payment ID during inventory failure compensation")
        void inventoryFailed_withNullPaymentId_shouldOnlyCancelOrder() {
            
            String orderId = UUID.randomUUID().toString();
            String reason = "Out of stock";

            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.PAYMENT_COMPLETED)
                    .paymentId(null) 
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            
            orchestrator.onInventoryFailed(orderId, reason);

            
            verify(compensationProducer, never()).requestPaymentRefund(any(), any(), any());
            verify(compensationProducer).requestOrderCancellation(orderId, reason);
        }
    }

    @Nested
    @DisplayName("Metrics Tests")
    class MetricsTests {

        @Test
        @DisplayName("Should increment compensations triggered counter on failure")
        void onFailure_shouldIncrementCompensationsTriggeredCounter() {
            
            String orderId = UUID.randomUUID().toString();
            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.PAYMENT_PENDING)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            double beforeCount = meterRegistry.counter("saga.compensations.triggered").count();

            
            orchestrator.onPaymentFailed(orderId, "Test failure");

            
            double afterCount = meterRegistry.counter("saga.compensations.triggered").count();
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }

        @Test
        @DisplayName("Should increment compensations completed counter on mark compensated")
        void markCompensated_shouldIncrementCompletedCounter() {
            
            String orderId = UUID.randomUUID().toString();
            SagaInstance saga = SagaInstance.builder()
                    .orderId(orderId)
                    .state(SagaState.COMPENSATING)
                    .build();

            when(sagaRepository.findById(orderId)).thenReturn(Optional.of(saga));
            when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(i -> i.getArgument(0));

            double beforeCount = meterRegistry.counter("saga.compensations.completed").count();

            
            orchestrator.markCompensated(orderId);

            
            double afterCount = meterRegistry.counter("saga.compensations.completed").count();
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }
    }
}
