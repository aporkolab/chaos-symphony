package hu.porkolab.chaosSymphony.orchestrator.pact;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.orchestrator.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(PactConsumerTestExt.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:pactdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@EmbeddedKafka(partitions = 1,
    topics = {"payment.request", "payment.result"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@PactTestFor(providerName = "payment-svc", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
@ContextConfiguration(classes = TestConfig.class)
@DisplayName("Orchestrator ↔ Payment Service Contract Tests")
public class PaymentSvcContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Pact(consumer = "orchestrator")
    public MessagePact createPaymentRequestedPact(MessagePactBuilder builder) {
        return builder
            .expectsToReceive("A payment requested event")
            .withContent(newJsonBody(envelope -> {
                envelope.stringType("orderId", "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46");
                envelope.stringType("eventId", "f8b5c2d1-3e4f-5a6b-7c8d-9e0f1a2b3c4d");
                envelope.stringType("type", "PaymentRequested");
                envelope.stringType("payload", "{\"orderId\":\"e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46\",\"amount\":123.45,\"currency\":\"USD\"}");
            }).build())
            .toPact();
    }


    @Pact(consumer = "orchestrator")
    public MessagePact createPaymentCompletedPact(MessagePactBuilder builder) {
        return builder
            .expectsToReceive("A payment completed event")
            .withContent(newJsonBody(envelope -> {
                envelope.stringType("orderId", "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46");
                envelope.stringType("eventId");
                envelope.stringType("type", "PaymentCompleted");
                envelope.stringType("payload");
            }).build())
            .toPact();
    }


    @Pact(consumer = "orchestrator")
    public MessagePact createPaymentFailedPact(MessagePactBuilder builder) {
        return builder
            .expectsToReceive("A payment failed event")
            .withContent(newJsonBody(envelope -> {
                envelope.stringType("orderId", "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46");
                envelope.stringType("eventId");
                envelope.stringType("type", "PaymentFailed");
                envelope.stringType("payload");
            }).build())
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentRequestedPact")
    @DisplayName("Should accept payment request message format")
    void testPaymentRequested(List<Message> messages) throws Exception {
        assertThat(messages).isNotEmpty();

        Message message = messages.get(0);
        String content = message.contentsAsString();


        JsonNode envelope = objectMapper.readTree(content);

        assertThat(envelope.has("orderId")).isTrue();
        assertThat(envelope.has("eventId")).isTrue();
        assertThat(envelope.has("type")).isTrue();
        assertThat(envelope.has("payload")).isTrue();


        String payloadStr = envelope.get("payload").asText();
        JsonNode payload = objectMapper.readTree(payloadStr);

        assertThat(payload.has("orderId")).isTrue();
        assertThat(payload.has("amount")).isTrue();
        assertThat(payload.has("currency")).isTrue();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentCompletedPact")
    @DisplayName("Should accept payment completed message format")
    void testPaymentCompleted(List<Message> messages) throws Exception {
        assertThat(messages).isNotEmpty();

        Message message = messages.get(0);
        JsonNode envelope = objectMapper.readTree(message.contentsAsString());

        assertThat(envelope.get("type").asText()).isEqualTo("PaymentCompleted");
        assertThat(envelope.has("orderId")).isTrue();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentFailedPact")
    @DisplayName("Should accept payment failed message format for compensation")
    void testPaymentFailed(List<Message> messages) throws Exception {
        assertThat(messages).isNotEmpty();

        Message message = messages.get(0);
        JsonNode envelope = objectMapper.readTree(message.contentsAsString());

        assertThat(envelope.get("type").asText()).isEqualTo("PaymentFailed");
        assertThat(envelope.has("orderId")).isTrue();
    }
}
