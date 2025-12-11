package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PaymentRequestedListenerTest {

    @Mock
    private PaymentResultProducer producer;
    
    @Mock
    private IdempotencyStore idempotencyStore;
    
    @Mock
    private PaymentStatusStore paymentStatusStore;
    
    @Mock
    private Counter paymentsProcessedMain;
    
    @Mock
    private Counter paymentsProcessedCanary;
    
    @Mock
    private Timer processingTime;
    
    private ObjectMapper objectMapper;
    
    private PaymentRequestedListener listener;

    private static final String ORDER_ID = "test-order-123";
    private static final double AMOUNT = 99.99;
    
    private String createEnvelope(String orderId, double amount) {
        return """
                {
                    "orderId": "%s",
                    "eventId": "event-456",
                    "type": "PaymentRequested",
                    "payload": "{\\"orderId\\": \\"%s\\", \\"amount\\": %s, \\"currency\\": \\"USD\\"}"
                }
                """.formatted(orderId, orderId, amount);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new PaymentRequestedListener(
                producer,
                idempotencyStore,
                paymentStatusStore,
                paymentsProcessedMain,
                paymentsProcessedCanary,
                processingTime,
                objectMapper
        );
        
        ReflectionTestUtils.setField(listener, "successRate", 1.0);
    }

    @Nested
    @DisplayName("Main Payment Processing")
    class MainPaymentTests {

        @Test
        @DisplayName("Should process valid payment request and send result")
        void shouldProcessValidPaymentRequest() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            listener.onPaymentRequested(record);
            
            
            verify(paymentsProcessedMain).increment();
            verify(idempotencyStore).markIfFirst(ORDER_ID);
            verify(paymentStatusStore).save(ORDER_ID, "CHARGED");
            
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
            
            String resultPayload = payloadCaptor.getValue();
            assertThat(resultPayload).contains("\"status\":\"CHARGED\"");
            assertThat(resultPayload).contains("\"amount\":" + AMOUNT);
            
            verify(processingTime).record(anyLong(), eq(TimeUnit.NANOSECONDS));
        }

        @Test
        @DisplayName("Should skip duplicate messages")
        void shouldSkipDuplicateMessages() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(false);
            
            
            listener.onPaymentRequested(record);
            
            
            verify(paymentsProcessedMain).increment();
            verify(idempotencyStore).markIfFirst(ORDER_ID);
            verify(paymentStatusStore, never()).save(anyString(), anyString());
            verify(producer, never()).sendResult(anyString(), anyString());
        }

        @Test
        @DisplayName("Should send FAILED result when payment processing fails")
        void shouldSendFailedResultWhenPaymentFails() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            ReflectionTestUtils.setField(listener, "successRate", 0.0);
            
            
            listener.onPaymentRequested(record);
            
            
            verify(paymentStatusStore).save(ORDER_ID, "FAILED");
            
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
            
            String resultPayload = payloadCaptor.getValue();
            assertThat(resultPayload).contains("\"status\":\"FAILED\"");
            assertThat(resultPayload).contains("Payment processing failed");
        }
    }

    @Nested
    @DisplayName("Canary Payment Processing")
    class CanaryPaymentTests {

        @Test
        @DisplayName("Should process canary payment request and send result")
        void shouldProcessCanaryPaymentRequest() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested.canary", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            listener.onPaymentRequestedCanary(record);
            
            
            verify(paymentsProcessedCanary).increment();
            verify(idempotencyStore).markIfFirst(ORDER_ID);
            verify(paymentStatusStore).save(ORDER_ID, "CHARGED");
            verify(producer).sendResult(eq(ORDER_ID), anyString());
        }

        @Test
        @DisplayName("Should skip duplicate canary messages")
        void shouldSkipDuplicateCanaryMessages() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested.canary", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(false);
            
            
            listener.onPaymentRequestedCanary(record);
            
            
            verify(paymentsProcessedCanary).increment();
            verify(producer, never()).sendResult(anyString(), anyString());
        }

        @Test
        @DisplayName("Should send FAILED result when canary payment processing fails")
        void shouldSendFailedResultWhenCanaryPaymentFails() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested.canary", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            ReflectionTestUtils.setField(listener, "successRate", 0.0);
            
            
            listener.onPaymentRequestedCanary(record);
            
            
            verify(paymentStatusStore).save(ORDER_ID, "FAILED");
            
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
            
            String resultPayload = payloadCaptor.getValue();
            assertThat(resultPayload).contains("\"status\":\"FAILED\"");
            assertThat(resultPayload).contains("[CANARY] Payment processing failed");
        }
    }

    @Nested
    @DisplayName("Metrics and Timing")
    class MetricsTests {

        @Test
        @DisplayName("Should record processing time even on failure")
        void shouldRecordProcessingTimeOnFailure() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            ReflectionTestUtils.setField(listener, "successRate", 0.0);
            
            
            listener.onPaymentRequested(record);
            
            
            verify(processingTime).record(anyLong(), eq(TimeUnit.NANOSECONDS));
        }

        @Test
        @DisplayName("Should increment correct counter for main traffic")
        void shouldIncrementMainCounter() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            listener.onPaymentRequested(record);
            
            
            verify(paymentsProcessedMain).increment();
            verify(paymentsProcessedCanary, never()).increment();
        }

        @Test
        @DisplayName("Should increment correct counter for canary traffic")
        void shouldIncrementCanaryCounter() throws Exception {
            
            String envelope = createEnvelope(ORDER_ID, AMOUNT);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "payment.requested.canary", 0, 0, ORDER_ID, envelope);
            when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
            
            
            listener.onPaymentRequestedCanary(record);
            
            
            verify(paymentsProcessedCanary).increment();
            verify(paymentsProcessedMain, never()).increment();
        }
    }
}
