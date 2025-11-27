package hu.porkolab.chaosSymphony.orderapi.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRequestProducerTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;

    private PaymentRequestProducer producer;

    @BeforeEach
    void setup() {
        producer = new PaymentRequestProducer(kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendPaymentRequest() throws Exception {
        String orderId = "order-123";
        String payloadJson = "{\"amount\":100.00}";

        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition("payment.requested", 0),
            0L, 0, 0L, 0, 0
        );

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq("payment.requested"), eq(orderId), any(String.class))).thenReturn(future);

        producer.sendRequest(orderId, payloadJson);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("payment.requested"), eq(orderId), messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertThat(sentMessage).contains("PaymentRequested");
        assertThat(sentMessage).contains(orderId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowExceptionOnSendFailure() {
        String orderId = "fail-order";
        String payloadJson = "{\"amount\":50.00}";

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(eq("payment.requested"), eq(orderId), any(String.class))).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.sendRequest(orderId, payloadJson))
            .isInstanceOf(RuntimeException.class);
    }
}
