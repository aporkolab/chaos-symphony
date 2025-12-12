package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;


@Slf4j
@Component
public class OrderCreatedListener {
    
    private final PaymentProducer producer;
    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public OrderCreatedListener(
            PaymentProducer producer, 
            SagaOrchestrator sagaOrchestrator,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, random = true),
            include = {SocketTimeoutException.class, IllegalStateException.class, 
                       ChaosProducer.ChaosDropException.class, RuntimeException.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = "order.created", groupId = "orchestrator-order-created")
    public void onOrderCreated(ConsumerRecord<String, String> rec) {
        JsonNode root;
        JsonNode event;
        try {
            String rawMessage = rec.value();
            root = objectMapper.readTree(rawMessage);
            
            String payloadStr;
            if (root.has("payload")) {
                JsonNode payloadNode = root.get("payload");
                
                if (payloadNode.isTextual()) {
                    payloadStr = payloadNode.asText();
                } else {
                    payloadStr = payloadNode.toString();
                }
            } else {
                payloadStr = rawMessage;
            }
            
            event = objectMapper.readTree(payloadStr);
        } catch (Exception e) {
            log.error("Failed to parse order.created message: {}", e.getMessage());
            return;
        }
        
        
        String orderId = event.path("orderId").asText(null);
        if (orderId == null || orderId.isBlank()) {
            log.error("Missing orderId in order.created event, skipping");
            return;
        }
        
        double total = event.path("total").asDouble(0.0);
        String currency = event.path("currency").asText("USD");
        String customerId = event.has("customerId") && !event.get("customerId").isNull() 
                ? event.get("customerId").asText() 
                : "N/A";
        String shippingAddress = event.has("shippingAddress") && !event.get("shippingAddress").isNull()
                ? event.get("shippingAddress").asText()
                : null;

        log.info("OrderCreated received for orderId={}, customerId={}, address={} -> initiating payment saga", 
                orderId, customerId, shippingAddress != null ? shippingAddress : "NOT_PROVIDED");

        
        sagaOrchestrator.startSagaAndRequestPayment(orderId, shippingAddress);

        String paymentPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("amount", total)
                .put("currency", currency)
                .toString();

        
        producer.sendPaymentRequested(orderId, paymentPayload);
        
        log.debug("PaymentRequested sent for orderId={}", orderId);
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> rec) {
        log.error("Message sent to DLT after all retries exhausted: key={}, topic={}", 
                rec.key(), rec.topic());
        sagaOrchestrator.recordDltMessage();
    }
}
