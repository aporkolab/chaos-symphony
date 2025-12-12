package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRefundListenerTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private PaymentStatusStore paymentStatusStore;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private PaymentRefundListener listener;

    private static final String ORDER_ID = "order-123";
    private static final String PAYMENT_ID = "pay-456";

    private String createRefundEnvelope(String orderId, String paymentId, String reason) {
        return """
                {
                    "orderId": "%s",
                    "eventId": "evt-789",
                    "type": "PaymentRefund",
                    "payload": "{\\"orderId\\": \\"%s\\", \\"paymentId\\": \\"%s\\", \\"reason\\": \\"%s\\"}"
                }
                """.formatted(orderId, orderId, paymentId, reason);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        listener = new PaymentRefundListener(
                objectMapper,
                idempotencyStore,
                paymentStatusStore,
                kafkaTemplate,
                meterRegistry
        );
    }

    @Nested
    @DisplayName("Refund Processing")
    class RefundProcessingTests {

        @Test
        @DisplayName("Should refund charged payment successfully")
        void shouldRefundChargedPayment() {
            String envelope = createRefundEnvelope(ORDER_ID, PAYMENT_ID, "Customer request");
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            when(paymentStatusStore.getStatus(ORDER_ID)).thenReturn(Optional.of("CHARGED"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            listener.onPaymentRefund(record);

            verify(paymentStatusStore).save(ORDER_ID, "REFUNDED");
            verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
        }

        @Test
        @DisplayName("Should skip already refunded payment")
        void shouldSkipAlreadyRefundedPayment() {
            String envelope = createRefundEnvelope(ORDER_ID, PAYMENT_ID, "Duplicate");
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            when(paymentStatusStore.getStatus(ORDER_ID)).thenReturn(Optional.of("REFUNDED"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            listener.onPaymentRefund(record);

            verify(paymentStatusStore, never()).save(anyString(), anyString());
            verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
        }

        @Test
        @DisplayName("Should handle unknown payment status")
        void shouldHandleUnknownPaymentStatus() {
            String envelope = createRefundEnvelope(ORDER_ID, PAYMENT_ID, "Test");
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            when(paymentStatusStore.getStatus(ORDER_ID)).thenReturn(Optional.empty());
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            listener.onPaymentRefund(record);

            verify(paymentStatusStore, never()).save(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip duplicate refund requests")
        void shouldSkipDuplicateRefundRequests() {
            String envelope = createRefundEnvelope(ORDER_ID, PAYMENT_ID, "Test");
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(false);

            listener.onPaymentRefund(record);

            verify(paymentStatusStore, never()).getStatus(anyString());
            verify(paymentStatusStore, never()).save(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid JSON gracefully")
        void shouldHandleInvalidJson() {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, "not-valid-json");

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            
            listener.onPaymentRefund(record);

            verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
        }

        @Test
        @DisplayName("Should handle missing orderId")
        void shouldHandleMissingOrderId() {
            String envelope = """
                    {
                        "orderId": "x",
                        "eventId": "evt-789",
                        "type": "PaymentRefund",
                        "payload": "{\\"paymentId\\": \\"pay-123\\"}"
                    }
                    """;
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);

            listener.onPaymentRefund(record);

            verify(paymentStatusStore, never()).save(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle pending payment status")
        void shouldHandlePendingPaymentStatus() {
            String envelope = createRefundEnvelope(ORDER_ID, PAYMENT_ID, "Test");
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.refund", 0, 0, ORDER_ID, envelope);

            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            when(paymentStatusStore.getStatus(ORDER_ID)).thenReturn(Optional.of("PENDING"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            listener.onPaymentRefund(record);

            verify(paymentStatusStore, never()).save(anyString(), anyString());
        }
    }
}
