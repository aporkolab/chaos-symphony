package hu.porkolab.chaosSymphony.payment.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;


@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-result-producer", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
public class PaymentResultContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Pact(consumer = "orchestrator")
    public MessagePact createPaymentResultPact(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("A payment result event")
                .withContent(newJsonBody(envelope -> {
                    envelope.stringType("orderId", "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46");
                    envelope.stringType("eventId", "f8b5c2d1-3e4f-5a6b-7c8d-9e0f1a2b3c4d");
                    envelope.stringType("type", "PaymentResult");
                    envelope.stringType("payload", "{\"orderId\":\"e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46\",\"status\":\"CHARGED\",\"amount\":123.45}");
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentResultPact")
    public void testPaymentResultMessage(List<Message> messages) throws Exception {
        
        assert !messages.isEmpty();
        
        
        Message message = messages.get(0);
        String messageBody = message.getContents().valueAsString();
        
        
        var envelope = EnvelopeHelper.parse(messageBody);
        assert envelope.getType().equals("PaymentResult");
        
        
        var payloadNode = objectMapper.readTree(envelope.getPayload());
        assert payloadNode.has("orderId");
        assert payloadNode.has("status");
        assert payloadNode.has("amount");
        assert payloadNode.path("status").asText().equals("CHARGED");
    }

}
