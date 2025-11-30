package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import io.micrometer.core.instrument.Counter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ShippingResultListenerTest {

    @Test
    void delivered_shouldCompleteSaga_andIncrementSucceeded() throws Exception {
        String orderId = "o1";

        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        Counter succeeded = mock(Counter.class);
        Counter failed = mock(Counter.class);

        when(idempotencyStore.markIfFirst(orderId)).thenReturn(true);

        ShippingResultListener listener = new ShippingResultListener(
            idempotencyStore,
            sagaOrchestrator,
            new ObjectMapper(),
            succeeded,
            failed
        );

        String payload = """
                {"orderId":"o1","shippingId":"s1","status":"DELIVERED"}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "c1", "ShippingResult", payload);

        ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("shipping.result", 0, 0L, orderId, envelope);

        listener.onResult(rec);

        verify(sagaOrchestrator).onShippingCompleted("o1", "s1");
        verify(succeeded).increment();
        verifyNoInteractions(failed);
    }

    @Test
    void failed_shouldTriggerCompensation_andIncrementFailed() throws Exception {
        String orderId = "o2";

        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        Counter succeeded = mock(Counter.class);
        Counter failed = mock(Counter.class);

        when(idempotencyStore.markIfFirst(orderId)).thenReturn(true);

        ShippingResultListener listener = new ShippingResultListener(
            idempotencyStore,
            sagaOrchestrator,
            new ObjectMapper(),
            succeeded,
            failed
        );

        String payload = """
                {"orderId":"o2","shippingId":"s2","status":"FAILED","reason":"addr error"}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "c2", "ShippingResult", payload);

        ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("shipping.result", 0, 0L, orderId, envelope);

        listener.onResult(rec);

        verify(sagaOrchestrator).onShippingFailed(eq("o2"), anyString());
        verify(failed).increment();
        verifyNoInteractions(succeeded);
    }

    @Test
    void unknownStatus_shouldCallOnShippingFailed_andIncrementFailed() throws Exception {
        String orderId = "o3";

        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        Counter succeeded = mock(Counter.class);
        Counter failed = mock(Counter.class);

        when(idempotencyStore.markIfFirst(orderId)).thenReturn(true);

        ShippingResultListener listener = new ShippingResultListener(
            idempotencyStore,
            sagaOrchestrator,
            new ObjectMapper(),
            succeeded,
            failed
        );

        String payload = """
                {"orderId":"o3","shippingId":"s3","status":"WEIRD_STATUS"}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "c3", "ShippingResult", payload);

        ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("shipping.result", 0, 0L, orderId, envelope);

        listener.onResult(rec);

        verify(sagaOrchestrator).onShippingFailed(eq("o3"), anyString());
        verify(failed).increment();
        verifyNoInteractions(succeeded);
    }

    @Test
    void duplicateMessage_shouldBeIgnored() throws Exception {
        String orderId = "dup";

        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        SagaOrchestrator sagaOrchestrator = mock(SagaOrchestrator.class);
        Counter succeeded = mock(Counter.class);
        Counter failed = mock(Counter.class);

        when(idempotencyStore.markIfFirst(orderId)).thenReturn(false);

        ShippingResultListener listener = new ShippingResultListener(
            idempotencyStore,
            sagaOrchestrator,
            new ObjectMapper(),
            succeeded,
            failed
        );

        String payload = """
                {"orderId":"dup","shippingId":"sX","status":"DELIVERED"}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "cX", "ShippingResult", payload);

        ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("shipping.result", 0, 0L, orderId, envelope);

        listener.onResult(rec);

        verifyNoInteractions(sagaOrchestrator);
        verifyNoInteractions(succeeded);
        verifyNoInteractions(failed);
    }
}
