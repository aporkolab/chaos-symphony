package hu.porkolab.chaosSymphony.payment.kafka;

import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.payment.outbox.IdempotentOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResultProducerTest {

    @Mock
    private ChaosProducer chaosProducer;

    @Mock
    private IdempotentOutbox outbox;

    private PaymentResultProducer producer;

    @BeforeEach
    void setUp() {
        producer = new PaymentResultProducer(chaosProducer, outbox);
    }

    @Test
    @DisplayName("Should send result when not duplicate")
    void shouldSendResultWhenNotDuplicate() {
        String orderId = "order-123";
        String payload = "{\"status\":\"CHARGED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(true);

        producer.sendResult(orderId, payload);

        verify(chaosProducer).send(eq("payment.result"), eq(orderId), eq("PaymentResult"), anyString());
    }

    @Test
    @DisplayName("Should suppress duplicate results")
    void shouldSuppressDuplicateResults() {
        String orderId = "order-123";
        String payload = "{\"status\":\"CHARGED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(false);

        producer.sendResult(orderId, payload);

        verify(chaosProducer, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should propagate ChaosDropException")
    void shouldPropagateChaosDropException() {
        String orderId = "order-123";
        String payload = "{\"status\":\"CHARGED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        doThrow(new ChaosProducer.ChaosDropException("Chaos drop"))
                .when(chaosProducer).send(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> producer.sendResult(orderId, payload))
                .isInstanceOf(ChaosProducer.ChaosDropException.class);
    }

    @Test
    @DisplayName("Should wrap other exceptions in RuntimeException")
    void shouldWrapOtherExceptionsInRuntimeException() {
        String orderId = "order-123";
        String payload = "{\"status\":\"CHARGED\"}";
        when(outbox.markIfFirst(anyString())).thenReturn(true);
        doThrow(new RuntimeException("Kafka error"))
                .when(chaosProducer).send(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> producer.sendResult(orderId, payload))
                .isInstanceOf(RuntimeException.class);
    }
}
