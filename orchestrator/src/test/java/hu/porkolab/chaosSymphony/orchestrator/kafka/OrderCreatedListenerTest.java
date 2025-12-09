package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.events.OrderCreated;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaInstance;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderCreatedListenerTest {

    @Test
    void onOrderCreated_shouldStartSagaAndSendPaymentRequest() throws Exception {
        PaymentProducer producer = mock(PaymentProducer.class);
        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();

        when(idempotencyStore.markIfFirst("o1")).thenReturn(true);

        SagaInstance saga = SagaInstance.builder()
            .orderId("o1")
            .state(SagaState.STARTED)
            .build();

        when(sagaOrchestrator.startSaga("o1")).thenReturn(saga);

        OrderCreatedListener listener =
            new OrderCreatedListener(producer, idempotencyStore, sagaOrchestrator, om);

        
        OrderCreated event = OrderCreated.newBuilder()
            .setOrderId("o1")
            .setTotal(123.0)
            .setCurrency("EUR")
            .setCustomerId("c1")
            .build();

        ConsumerRecord<String, OrderCreated> record =
            new ConsumerRecord<>("order.created", 0, 0L, "o1", event);

        listener.onOrderCreated(record);

        verify(sagaOrchestrator).startSaga("o1");
        verify(producer).sendPaymentRequested(eq("o1"), anyString());
    }


    @Test
    void duplicateMessage_shouldBeIgnored() throws Exception {
        PaymentProducer producer = mock(PaymentProducer.class);
        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();

        when(idempotencyStore.markIfFirst("dup")).thenReturn(false);

        OrderCreatedListener listener =
            new OrderCreatedListener(producer, idempotencyStore, sagaOrchestrator, om);

        ConsumerRecord<String, OrderCreated> record =
            new ConsumerRecord<>("order.created", 0, 0L, "dup",
                OrderCreated.newBuilder()
                    .setOrderId("dup")
                    .setTotal(1.0)
                    .setCurrency("EUR")
                    .setCustomerId("x")
                    .build());

        listener.onOrderCreated(record);

        verifyNoInteractions(producer);
        verifyNoInteractions(sagaOrchestrator);
    }
}
