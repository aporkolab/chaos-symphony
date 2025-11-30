package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaInstance;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCreatedListenerTest {

    private PaymentProducer producer;
    private SagaOrchestrator sagaOrchestrator;
    private ObjectMapper objectMapper;
    private OrderCreatedListener listener;

    @BeforeEach
    void setUp() {
        producer = mock(PaymentProducer.class);
        sagaOrchestrator = mock(SagaOrchestrator.class);
        objectMapper = new ObjectMapper();
        listener = new OrderCreatedListener(producer, sagaOrchestrator, objectMapper);
    }

    @Test
    void onOrderCreated_shouldStartSagaAndSendPaymentRequest() throws Exception {
        SagaInstance mockSaga = mock(SagaInstance.class);
        when(sagaOrchestrator.startSagaAndRequestPayment(anyString(), any())).thenReturn(mockSaga);

        String payload = "{\"schema\":{\"type\":\"string\"},\"payload\":\"{\\\"orderId\\\":\\\"order-123\\\",\\\"total\\\":100.0,\\\"currency\\\":\\\"USD\\\",\\\"customerId\\\":\\\"cust-1\\\"}\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.created", 0, 0, "order-123", payload);

        listener.onOrderCreated(record);

        verify(sagaOrchestrator).startSagaAndRequestPayment(eq("order-123"), isNull());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendPaymentRequested(eq("order-123"), captor.capture());
        assertThat(captor.getValue()).contains("\"orderId\":\"order-123\"");
    }

    @Test
    void onOrderCreated_shouldHandleMissingOrderId() throws Exception {
        String payload = "{\"total\":100.0,\"currency\":\"USD\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.created", 0, 0, "key", payload);

        listener.onOrderCreated(record);

        verify(sagaOrchestrator, never()).startSagaAndRequestPayment(anyString(), any());
        verify(producer, never()).sendPaymentRequested(anyString(), anyString());
    }
}
