package hu.porkolab.chaosSymphony.shipping.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ShippingRequestedListenerTest {

    @Mock
    private ShippingResultProducer producer;
    
    @Mock
    private IdempotencyStore idempotencyStore;
    
    @Mock
    private Counter messagesProcessed;
    
    @Mock
    private Timer processingTime;
    
    private ObjectMapper objectMapper;
    
    private ShippingRequestedListener listener;

    private static final String ORDER_ID = "test-order-123";
    private static final String TEST_ADDRESS = "123 Test Street, Budapest";
    
    private String createEnvelope(String address) {
        return """
                {
                    "orderId": "%s",
                    "eventId": "event-456",
                    "type": "ShippingRequested",
                    "payload": "{\\"address\\": \\"%s\\"}"
                }
                """.formatted(ORDER_ID, address);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ShippingRequestedListener(
                producer, 
                idempotencyStore, 
                messagesProcessed, 
                processingTime,
                objectMapper
        );
        
        ReflectionTestUtils.setField(listener, "successRate", 1.0);
    }

    @Test
    @DisplayName("Should process valid shipping request and send result")
    void shouldProcessValidShippingRequest() throws Exception {
        
        String envelope = createEnvelope(TEST_ADDRESS);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "shipping.requested", 0, 0, ORDER_ID, envelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
        
        
        listener.onShippingRequested(record);
        
        
        verify(messagesProcessed).increment();
        verify(idempotencyStore).markIfFirst(ORDER_ID);
        
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
        
        String resultPayload = payloadCaptor.getValue();
        assertThat(resultPayload).contains("\"status\":\"SHIPPED\"");
        assertThat(resultPayload).contains(TEST_ADDRESS);
        
        verify(processingTime).record(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    @DisplayName("Should skip duplicate messages")
    void shouldSkipDuplicateMessages() throws Exception {
        
        String envelope = createEnvelope(TEST_ADDRESS);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "shipping.requested", 0, 0, ORDER_ID, envelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(false);
        
        
        listener.onShippingRequested(record);
        
        
        verify(messagesProcessed).increment();
        verify(idempotencyStore).markIfFirst(ORDER_ID);
        verify(producer, never()).sendResult(anyString(), anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "   "})
    @DisplayName("Should send FAILED status for invalid addresses")
    void shouldSendFailedForInvalidAddresses(String invalidAddress) {
        
        String addressValue = invalidAddress == null ? "" : invalidAddress;
        String envelope = """
                {
                    "orderId": "%s",
                    "eventId": "event-456",
                    "type": "ShippingRequested",
                    "payload": "{\\"address\\": \\"%s\\"}"
                }
                """.formatted(ORDER_ID, addressValue);
        
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "shipping.requested", 0, 0, ORDER_ID, envelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
        
        
        listener.onShippingRequested(record);
        
        
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
        
        String resultPayload = payloadCaptor.getValue();
        assertThat(resultPayload).contains("\"status\":\"FAILED\"");
        assertThat(resultPayload).contains("Invalid shipping address");
    }

    @Test
    @DisplayName("Should send FAILED when carrier unavailable")
    void shouldSendFailedWhenCarrierUnavailable() {
        
        String envelope = createEnvelope(TEST_ADDRESS);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "shipping.requested", 0, 0, ORDER_ID, envelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
        
        
        ReflectionTestUtils.setField(listener, "successRate", 0.0);
        
        
        listener.onShippingRequested(record);
        
        
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());
        
        String resultPayload = payloadCaptor.getValue();
        assertThat(resultPayload).contains("\"status\":\"FAILED\"");
        assertThat(resultPayload).contains("Shipping carrier unavailable");
    }

    @Test
    @DisplayName("Should record processing time even on failure")
    void shouldRecordProcessingTimeOnFailure() {
        
        String envelope = createEnvelope("UNKNOWN");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "shipping.requested", 0, 0, ORDER_ID, envelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);
        
        
        listener.onShippingRequested(record);
        
        
        verify(processingTime).record(anyLong(), eq(TimeUnit.NANOSECONDS));
    }
}
