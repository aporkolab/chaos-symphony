package hu.porkolab.chaosSymphony.shipping.kafka;

import hu.porkolab.chaosSymphony.shipping.outbox.IdempotentOutbox;
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
class ShippingResultProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private IdempotentOutbox outbox;

    private ShippingResultProducer producer;

    @BeforeEach
    void setUp() {
        producer = new ShippingResultProducer(kafkaTemplate, outbox);
    }

    @Test
    @DisplayName("Should send result when not duplicate")
    void shouldSendResultWhenNotDuplicate() {
        String orderId = "order-123";
        String payload = "{\"status\":\"SHIPPED\"}";
        
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        
        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("shipping.result", 0), 0, 0, 0L, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.sendResult(orderId, payload);

        verify(kafkaTemplate).send(eq("shipping.result"), eq(orderId), anyString());
    }

    @Test
    @DisplayName("Should suppress duplicate results")
    void shouldSuppressDuplicateResults() {
        String orderId = "order-123";
        String payload = "{\"status\":\"SHIPPED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(false);

        producer.sendResult(orderId, payload);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should wrap exceptions in RuntimeException")
    void shouldWrapExceptionsInRuntimeException() {
        String orderId = "order-123";
        String payload = "{\"status\":\"SHIPPED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.sendResult(orderId, payload))
                .isInstanceOf(RuntimeException.class);
    }
}
