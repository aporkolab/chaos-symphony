package hu.porkolab.chaosSymphony.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.common.idemp.NoopIdempotencyStore;
import hu.porkolab.chaosSymphony.orchestrator.kafka.InventoryRequestProducer;
import hu.porkolab.chaosSymphony.orchestrator.kafka.PaymentResultListener;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OrchestratorFlowSmokeTest {

    @Test
    void paymentChargedTriggersInventoryRequest() throws Exception {
        var reg = new SimpleMeterRegistry();
        Counter failed = Counter.builder("orders.failed").register(reg);

        var mockedProducer = mock(InventoryRequestProducer.class);
        var mockedSaga = mock(SagaOrchestrator.class);

        var objectMapper = new ObjectMapper();

        var listener = new PaymentResultListener(
            objectMapper,
            new NoopIdempotencyStore(),
            mockedProducer,
            mockedSaga,
            failed
        );

        String payload = """
                {"eventId":"e1","correlationId":"c1",
                "payload":"{\\"status\\":\\"CHARGED\\",\\"orderId\\":\\"o1\\"}"}
                """;

        listener.onPaymentResult(new ConsumerRecord<>("payment.result", 0, 0, "o1", payload));

        verify(mockedProducer, times(1)).sendRequest(eq("o1"), anyString());
        assertEquals(0.0, failed.count());
    }

    @Test
    void paymentFailedIncrementsFailedCounter() throws Exception {
        var reg = new SimpleMeterRegistry();
        Counter failed = Counter.builder("orders.failed").register(reg);

        var mockedProducer = mock(InventoryRequestProducer.class);
        var mockedSaga = mock(SagaOrchestrator.class);

        var objectMapper = new ObjectMapper();

        var listener = new PaymentResultListener(
            objectMapper,
            new NoopIdempotencyStore(),
            mockedProducer,
            mockedSaga,
            failed
        );

        String payload = """
                {"eventId":"e1","correlationId":"c1",
                "payload":"{\\"status\\":\\"CHARGE_FAILED\\",\\"orderId\\":\\"o1\\"}"}
                """;

        listener.onPaymentResult(new ConsumerRecord<>("payment.result", 0, 0, "o1", payload));

        verify(mockedProducer, never()).sendRequest(anyString(), anyString());
        assertEquals(1.0, failed.count());
    }

    @Test
    void duplicateMessageShouldBeSkipped() throws Exception {
        var mockedStore = mock(IdempotencyStore.class);
        when(mockedStore.markIfFirst("o1")).thenReturn(false); 

        var reg = new SimpleMeterRegistry();
        Counter failed = Counter.builder("orders.failed").register(reg);
        var mockedProducer = mock(InventoryRequestProducer.class);
        var mockedSaga = mock(SagaOrchestrator.class);

        var listener = new PaymentResultListener(
            new ObjectMapper(),
            mockedStore,
            mockedProducer,
            mockedSaga,
            failed
        );

        String payload = """
        {"eventId":"e1","correlationId":"c1",
        "payload":"{\\"status\\":\\"CHARGED\\",\\"orderId\\":\\"o1\\"}"}
        """;

        listener.onPaymentResult(new ConsumerRecord<>("payment.result", 0, 0, "o1", payload));

        
        verifyNoInteractions(mockedProducer, mockedSaga);
        assertEquals(0.0, failed.count());
    }
    @Test
    void paymentFailedShouldCallSagaOnPaymentFailed() throws Exception {
        var reg = new SimpleMeterRegistry();
        Counter failed = Counter.builder("orders.failed").register(reg);

        var mockedProducer = mock(InventoryRequestProducer.class);
        var mockedSaga = mock(SagaOrchestrator.class);
        var mockedStore = mock(IdempotencyStore.class);

        when(mockedStore.markIfFirst("o1")).thenReturn(true);

        var listener = new PaymentResultListener(
            new ObjectMapper(),
            mockedStore,
            mockedProducer,
            mockedSaga,
            failed
        );

        String payload = """
        {"eventId":"e1","correlationId":"c1",
        "payload":"{\\"status\\":\\"CHARGE_FAILED\\",\\"orderId\\":\\"o1\\",\\"reason\\":\\"Insufficient\\"}"}
        """;

        listener.onPaymentResult(new ConsumerRecord<>("payment.result", 0, 0, "o1", payload));

        verify(mockedSaga, times(1)).onPaymentFailed(eq("o1"), eq("Insufficient"));
        verify(mockedProducer, never()).sendRequest(any(), any());
        assertEquals(1.0, failed.count());
    }

}
