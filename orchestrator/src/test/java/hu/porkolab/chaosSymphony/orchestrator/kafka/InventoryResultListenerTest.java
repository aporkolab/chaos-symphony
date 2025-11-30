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

class InventoryResultListenerTest {

    @Test
    void reservedStatus_shouldUpdateSagaAndSendShippingRequest() throws Exception {
        
        IdempotencyStore idemp = mock(IdempotencyStore.class);
        ShippingRequestProducer shipping = mock(ShippingRequestProducer.class);
        SagaOrchestrator saga = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();
        Counter ordersFailed = mock(Counter.class);

        when(idemp.markIfFirst("o1")).thenReturn(true);

        InventoryResultListener listener =
            new InventoryResultListener(idemp, shipping, saga, om, ordersFailed);

        String innerPayload = """
            {"status":"RESERVED","reservationId":"r1"}
            """;

        String envelope = EnvelopeHelper.envelope(
            "o1",
            "corr-1",
            "InventoryResult",
            innerPayload
        );

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("inventory.result", 0, 0L, "o1", envelope);

        
        listener.onResult(record);

        
        verify(saga).onInventoryReserved("o1", "r1");
        verify(shipping).sendRequest(eq("o1"), anyString());
        verifyNoInteractions(ordersFailed);
    }

    @Test
    void outOfStock_shouldCallInventoryFailedAndIncrementCounter() throws Exception {
        IdempotencyStore idemp = mock(IdempotencyStore.class);
        ShippingRequestProducer shipping = mock(ShippingRequestProducer.class);
        SagaOrchestrator saga = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();
        Counter ordersFailed = mock(Counter.class);

        when(idemp.markIfFirst("o2")).thenReturn(true);

        InventoryResultListener listener =
            new InventoryResultListener(idemp, shipping, saga, om, ordersFailed);

        String innerPayload = """
            {"status":"OUT_OF_STOCK"}
            """;

        String envelope = EnvelopeHelper.envelope(
            "o2",
            "corr-2",
            "InventoryResult",
            innerPayload
        );

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("inventory.result", 0, 0L, "o2", envelope);

        listener.onResult(record);

        verify(saga).onInventoryFailed("o2", "Inventory out of stock");
        verify(ordersFailed).increment();
        verifyNoInteractions(shipping);
    }

    @Test
    void unknownStatus_shouldCallInventoryFailedWithUnknownReason() throws Exception {
        IdempotencyStore idemp = mock(IdempotencyStore.class);
        ShippingRequestProducer shipping = mock(ShippingRequestProducer.class);
        SagaOrchestrator saga = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();
        Counter ordersFailed = mock(Counter.class);

        when(idemp.markIfFirst("o3")).thenReturn(true);

        InventoryResultListener listener =
            new InventoryResultListener(idemp, shipping, saga, om, ordersFailed);

        String innerPayload = """
            {"status":"WTF_STATUS"}
            """;

        String envelope = EnvelopeHelper.envelope(
            "o3",
            "corr-3",
            "InventoryResult",
            innerPayload
        );

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("inventory.result", 0, 0L, "o3", envelope);

        listener.onResult(record);

        verify(saga).onInventoryFailed("o3", "Unknown inventory status: WTF_STATUS");
        verify(ordersFailed).increment();
        verifyNoInteractions(shipping);
    }

    @Test
    void duplicateMessage_shouldBeIgnored() throws Exception {
        IdempotencyStore idemp = mock(IdempotencyStore.class);
        ShippingRequestProducer shipping = mock(ShippingRequestProducer.class);
        SagaOrchestrator saga = mock(SagaOrchestrator.class);
        ObjectMapper om = new ObjectMapper();
        Counter ordersFailed = mock(Counter.class);

        when(idemp.markIfFirst("dup")).thenReturn(false);

        InventoryResultListener listener =
            new InventoryResultListener(idemp, shipping, saga, om, ordersFailed);

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("inventory.result", 0, 0L, "dup", "whatever");

        listener.onResult(record);

        verifyNoInteractions(saga);
        verifyNoInteractions(shipping);
        verifyNoInteractions(ordersFailed);
    }
}
