package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentResultListenerTest {

    private ObjectMapper objectMapper;
    private InventoryRequestProducer inventoryProducer;
    private OrderCompensationProducer compensationProducer;

    private PaymentResultListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        inventoryProducer = mock(InventoryRequestProducer.class);
        compensationProducer = mock(OrderCompensationProducer.class);

        listener = new PaymentResultListener(
            objectMapper,
            inventoryProducer,
            compensationProducer
        );
    }

    @Test
    void onResult_shouldTriggerInventoryOnCharged() {
        String orderId = "o-success";
        String payload = """
                {"status":"CHARGED","items":3}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "PaymentResult", payload);

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("payment.result", 0, 0L, orderId, envelope);

        listener.onResult(record);

        verify(inventoryProducer).sendRequest(eq(orderId), anyString());
        verifyNoInteractions(compensationProducer);
    }

    @Test
    void onResult_shouldTriggerCompensationOnFailure() {
        String orderId = "o-fail";
        String payload = """
                {"status":"DECLINED"}
                """;

        String envelope = EnvelopeHelper.envelope(orderId, "PaymentResult", payload);

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("payment.result", 0, 0L, orderId, envelope);

        listener.onResult(record);

        verify(compensationProducer).sendCompensation(eq(orderId), anyString());
        verifyNoInteractions(inventoryProducer);
    }

    @Test
    void onResult_shouldSwallowMalformedPayload() {
        String orderId = "o-bad";
        String badValue = "not-a-valid-envelope";

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("payment.result", 0, 0L, orderId, badValue);

        
        listener.onResult(record);

        verifyNoInteractions(inventoryProducer, compensationProducer);
    }
}
