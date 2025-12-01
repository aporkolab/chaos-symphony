package hu.porkolab.chaosSymphony.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PaymentResult envelope format validation.
 * This is separate from Pact contract tests.
 */
public class PaymentResultEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testPaymentResultEnvelopeFormat() throws Exception {
        // Test that we can create payment result messages in the correct envelope format
        String orderId = "test-order-123";
        String eventId = "test-event-456";
        String resultPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("status", "CHARGED")
                .put("amount", 99.99)
                .toString();
        
        // Create envelope
        String envelopedMessage = EnvelopeHelper.envelope(orderId, eventId, "PaymentResult", resultPayload);
        var envelope = EnvelopeHelper.parse(envelopedMessage);
        
        // Verify envelope structure
        assertEquals("PaymentResult", envelope.getType());
        assertEquals(orderId, envelope.getOrderId());
        assertEquals(eventId, envelope.getEventId());
        
        // Verify payload content
        var payloadNode = objectMapper.readTree(envelope.getPayload());
        assertTrue(payloadNode.has("orderId"));
        assertTrue(payloadNode.has("status"));
        assertTrue(payloadNode.has("amount"));
        assertEquals("CHARGED", payloadNode.path("status").asText());
        assertEquals(orderId, payloadNode.path("orderId").asText());
        assertEquals(99.99, payloadNode.path("amount").asDouble());
    }
}
