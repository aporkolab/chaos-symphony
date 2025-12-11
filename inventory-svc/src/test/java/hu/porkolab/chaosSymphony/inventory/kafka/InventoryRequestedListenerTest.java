package hu.porkolab.chaosSymphony.inventory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class InventoryRequestedListenerTest {

    @Mock
    private InventoryResultProducer producer;

    @Mock
    private InventoryReleaseListener releaseListener;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private Counter messagesProcessed;

    @Mock
    private Timer processingTime;

    private ObjectMapper objectMapper;

    private InventoryRequestedListener listener;

    private static final String ORDER_ID = "test-order-123";
    private static final String VALID_ENVELOPE = """
            {
                "orderId": "%s",
                "eventId": "event-456",
                "type": "InventoryRequested",
                "payload": "{\\"items\\": 5}"
            }
            """.formatted(ORDER_ID);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new InventoryRequestedListener(
            producer,
            idempotencyStore,
            releaseListener,
            messagesProcessed,
            processingTime,
            objectMapper
        );

        ReflectionTestUtils.setField(listener, "successRate", 1.0);
    }

    @Test
    @DisplayName("Should process valid inventory request and send result")
    void shouldProcessValidInventoryRequest() throws Exception {

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "inventory.requested", 0, 0, ORDER_ID, VALID_ENVELOPE);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);


        listener.onInventoryRequested(record);


        verify(messagesProcessed).increment();
        verify(idempotencyStore).markIfFirst(ORDER_ID);

        
        verify(releaseListener).trackReservation(eq(ORDER_ID), anyString());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());

        String resultPayload = payloadCaptor.getValue();
        assertThat(resultPayload).contains("\"status\":\"RESERVED\"");
        assertThat(resultPayload).contains("\"items\":5");

        verify(processingTime).record(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    @DisplayName("Should skip duplicate messages")
    void shouldSkipDuplicateMessages() throws Exception {

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "inventory.requested", 0, 0, ORDER_ID, VALID_ENVELOPE);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(false);


        listener.onInventoryRequested(record);


        verify(messagesProcessed).increment();
        verify(idempotencyStore).markIfFirst(ORDER_ID);
        verify(producer, never()).sendResult(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject invalid item count")
    void shouldRejectInvalidItemCount() {

        String invalidEnvelope = """
                {
                    "orderId": "%s",
                    "eventId": "event-456",
                    "type": "InventoryRequested",
                    "payload": "{\\"items\\": 0}"
                }
                """.formatted(ORDER_ID);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "inventory.requested", 0, 0, ORDER_ID, invalidEnvelope);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);


        assertThatThrownBy(() -> listener.onInventoryRequested(record))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid item count");
    }

    @Test
    @DisplayName("Should throw when inventory unavailable")
    void shouldThrowWhenInventoryUnavailable() {

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "inventory.requested", 0, 0, ORDER_ID, VALID_ENVELOPE);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);


        ReflectionTestUtils.setField(listener, "successRate", 0.0);


        assertThatThrownBy(() -> listener.onInventoryRequested(record))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Inventory unavailable");
    }

    @Test
    @DisplayName("Should use default item count when not specified")
    void shouldUseDefaultItemCount() throws Exception {

        String envelopeWithoutItems = """
                {
                    "orderId": "%s",
                    "eventId": "event-456",
                    "type": "InventoryRequested",
                    "payload": "{}"
                }
                """.formatted(ORDER_ID);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "inventory.requested", 0, 0, ORDER_ID, envelopeWithoutItems);
        when(idempotencyStore.markIfFirst(ORDER_ID)).thenReturn(true);


        listener.onInventoryRequested(record);


        verify(releaseListener).trackReservation(eq(ORDER_ID), anyString());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendResult(eq(ORDER_ID), payloadCaptor.capture());

        String resultPayload = payloadCaptor.getValue();
        assertThat(resultPayload).contains("\"items\":1");
    }
}
