package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaState;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketTimeoutException;


@Slf4j
@Component
public class OrderCreatedListener {
    
    private final PaymentProducer producer;
    private final IdempotencyStore idempotencyStore;
    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public OrderCreatedListener(
            PaymentProducer producer, 
            IdempotencyStore idempotencyStore,
            SagaOrchestrator sagaOrchestrator,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.idempotencyStore = idempotencyStore;
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, random = true),
            include = {SocketTimeoutException.class, IllegalStateException.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = "order.created", groupId = "orchestrator-order-created")
    @Transactional
    public void onOrderCreated(ConsumerRecord<String, String> rec) throws Exception {
        String messageKey = rec.key();
        
        if (!idempotencyStore.markIfFirst(messageKey)) {
            log.warn("Duplicate message detected, skipping: {}", messageKey);
            return;
        }

        
        String rawMessage = rec.value();
        JsonNode root = objectMapper.readTree(rawMessage);
        
        
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
        
        
        JsonNode event = objectMapper.readTree(payloadStr);
        
        String orderId = event.get("orderId").asText();
        double total = event.get("total").asDouble();
        String currency = event.get("currency").asText();
        String customerId = event.has("customerId") && !event.get("customerId").isNull() 
                ? event.get("customerId").asText() 
                : "N/A";

        log.info("OrderCreated received for orderId={}, customerId={} -> initiating payment saga", 
                orderId, customerId);

        var saga = sagaOrchestrator.startSaga(orderId);
        saga.transitionTo(SagaState.PAYMENT_PENDING);

        String paymentPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("amount", total)
                .put("currency", currency)
                .toString();

        producer.sendPaymentRequested(orderId, paymentPayload);
        
        log.debug("PaymentRequested sent for orderId={}", orderId);
    }
}
