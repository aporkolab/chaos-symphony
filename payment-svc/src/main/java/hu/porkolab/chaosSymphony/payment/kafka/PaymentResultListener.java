package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class PaymentResultListener {

    private final ObjectMapper objectMapper;
    private final InventoryRequestProducer inventoryProducer;
    private final OrderCompensationProducer compensationProducer;

    public PaymentResultListener(
            ObjectMapper objectMapper,
            InventoryRequestProducer inventoryProducer,
            OrderCompensationProducer compensationProducer) {
        this.objectMapper = objectMapper;
        this.inventoryProducer = inventoryProducer;
        this.compensationProducer = compensationProducer;
    }

    @KafkaListener(topics = "payment.result", groupId = "orchestrator-1")
    public void onResult(ConsumerRecord<String, String> rec) {
        try {
            EventEnvelope envelope = EnvelopeHelper.parse(rec.value());
            String orderId = envelope.getOrderId();

            JsonNode message = objectMapper.readTree(envelope.getPayload());
            String status = message.path("status").asText();

            log.info("PaymentResult received for orderId={} with status={}", orderId, status);

            if ("CHARGED".equalsIgnoreCase(status)) {
                handleSuccessfulPayment(orderId, message);
            } else {
                handleFailedPayment(orderId, status);
            }

        } catch (Exception e) {
            log.error("Error processing PaymentResult message: {}", rec.value(), e);
        }
    }

    private void handleSuccessfulPayment(String orderId, JsonNode paymentMessage) {
        ObjectNode inventoryPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("items", paymentMessage.path("items").asInt(1));
        
        inventoryProducer.sendRequest(orderId, inventoryPayload.toString());
        log.info("Inventory reservation requested for orderId={}", orderId);
    }

    private void handleFailedPayment(String orderId, String status) {
        ObjectNode compensationPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("reason", "PAYMENT_FAILED")
                .put("originalStatus", status);
        
        compensationProducer.sendCompensation(orderId, compensationPayload.toString());
        log.warn("Payment failed, compensation requested for orderId={}", orderId);
    }
}
