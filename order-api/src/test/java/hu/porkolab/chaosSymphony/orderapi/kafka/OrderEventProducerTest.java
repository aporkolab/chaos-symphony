package hu.porkolab.chaosSymphony.orderapi.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;

    private OrderEventProducer producer;

    @BeforeEach
    void setup() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        producer = new OrderEventProducer(kafkaTemplate);
    }

    @Test
    void shouldSendOutboxEvent() {
        String orderId = "order-123";
        String outboxJson = "{\"type\":\"OrderCreated\",\"payload\":{}}";

        producer.sendOutbox(orderId, outboxJson);

        verify(kafkaTemplate).send("ordersdb.public.order_outbox", orderId, outboxJson);
    }

    @Test
    void shouldSendWithDifferentOrderId() {
        String orderId = "550e8400-e29b-41d4-a716-446655440000";
        String outboxJson = "{\"aggregateId\":\"" + orderId + "\"}";

        producer.sendOutbox(orderId, outboxJson);

        verify(kafkaTemplate).send("ordersdb.public.order_outbox", orderId, outboxJson);
    }
}
