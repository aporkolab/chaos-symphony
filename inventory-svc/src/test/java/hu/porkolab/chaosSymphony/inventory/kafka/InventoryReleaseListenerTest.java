package hu.porkolab.chaosSymphony.inventory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReleaseListenerTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private InventoryReleaseListener listener;

    private static final String ORDER_ID = "order-123";
    private static final String RESERVATION_ID = "res-456";

    private static final String VALID_ENVELOPE = """
            {"orderId":"order-123","eventId":"evt-789","type":"InventoryRelease","payload":"{\\"orderId\\":\\"order-123\\",\\"reservationId\\":\\"res-456\\",\\"reason\\":\\"Test\\"}"}
            """.trim();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new InventoryReleaseListener(
                objectMapper,
                idempotencyStore,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("Should release inventory with reservation ID")
    void shouldReleaseInventoryWithReservationId() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, VALID_ENVELOPE);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.onInventoryRelease(record);

        verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
    }

    @Test
    @DisplayName("Should release tracked reservation")
    void shouldReleaseTrackedReservation() {
        listener.trackReservation(ORDER_ID, RESERVATION_ID);

        String envelope = """
                {"orderId":"order-123","eventId":"evt-789","type":"InventoryRelease","payload":"{\\"orderId\\":\\"order-123\\",\\"reason\\":\\"cancelled\\"}"}
                """.trim();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, envelope);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.onInventoryRelease(record);

        verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
    }

    @Test
    @DisplayName("Should skip duplicate release requests")
    void shouldSkipDuplicateReleaseRequests() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, VALID_ENVELOPE);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(false);

        listener.onInventoryRelease(record);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully")
    void shouldHandleInvalidJson() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, "not-valid-json");

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.onInventoryRelease(record);

        verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
    }

    @Test
    @DisplayName("Should handle missing orderId in payload")
    void shouldHandleMissingOrderIdInPayload() {
        String envelope = """
                {"orderId":"x","eventId":"evt-789","type":"InventoryRelease","payload":"{\\"reservationId\\":\\"res-123\\"}"}
                """.trim();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, envelope);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);

        listener.onInventoryRelease(record);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should track reservation")
    void shouldTrackReservation() {
        listener.trackReservation(ORDER_ID, RESERVATION_ID);

        String envelope = """
                {"orderId":"order-123","eventId":"evt-789","type":"InventoryRelease","payload":"{\\"orderId\\":\\"order-123\\",\\"reason\\":\\"Test\\"}"}
                """.trim();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, envelope);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.onInventoryRelease(record);

        verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
    }

    @Test
    @DisplayName("Should handle no reservation found")
    void shouldHandleNoReservationFound() {
        String envelope = """
                {"orderId":"order-123","eventId":"evt-789","type":"InventoryRelease","payload":"{\\"orderId\\":\\"order-123\\",\\"reason\\":\\"Test\\"}"}
                """.trim();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.release", 0, 0, ORDER_ID, envelope);

        when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.onInventoryRelease(record);

        verify(kafkaTemplate).send(eq("compensation.result"), eq(ORDER_ID), anyString());
    }
}
