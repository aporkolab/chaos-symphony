package hu.porkolab.chaosSymphony.inventory.kafka;

import hu.porkolab.chaosSymphony.inventory.outbox.IdempotentOutbox;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryResultProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private IdempotentOutbox outbox;

    private InventoryResultProducer producer;

    @BeforeEach
    void setUp() {
        producer = new InventoryResultProducer(kafkaTemplate, outbox);
    }

    @Test
    @DisplayName("Should send result when not duplicate")
    void shouldSendResultWhenNotDuplicate() {
        String orderId = "order-123";
        String payload = "{\"status\":\"RESERVED\"}";
        
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        
        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("inventory.result", 0), 0, 0, 0L, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.sendResult(orderId, payload);

        verify(kafkaTemplate).send(eq("inventory.result"), eq(orderId), anyString());
    }

    @Test
    @DisplayName("Should suppress duplicate results")
    void shouldSuppressDuplicateResults() {
        String orderId = "order-123";
        String payload = "{\"status\":\"RESERVED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(false);

        producer.sendResult(orderId, payload);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should wrap exceptions in RuntimeException")
    void shouldWrapExceptionsInRuntimeException() {
        String orderId = "order-123";
        String payload = "{\"status\":\"RESERVED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.sendResult(orderId, payload))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should use orderId and payload hash as outbox key")
    void shouldUseCompositeOutboxKey() {
        String orderId = "order-456";
        String payload = "{\"status\":\"FAILED\"}";
        
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        
        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("inventory.result", 0), 0, 0, 0L, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.sendResult(orderId, payload);

        String expectedKeyPrefix = orderId + "|";
        verify(outbox).markIfFirst(argThat(key -> key.startsWith(expectedKeyPrefix)));
    }
}
