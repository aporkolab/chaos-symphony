package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultListener {

    private final ObjectMapper om;
    private final IdempotencyStore idempotencyStore;
    private final InventoryRequestProducer inventoryProducer;
    private final SagaOrchestrator sagaOrchestrator;
    private final Counter ordersFailed;

    @KafkaListener(topics = "payment.result", groupId = "orchestrator-payment-result")
    @Transactional
    public void onPaymentResult(ConsumerRecord<String, String> rec) {
        if (!idempotencyStore.markIfFirst(rec.key())) {
            log.warn("Duplicate message detected, skipping: {}", rec.key());
            return;
        }

        EventEnvelope env;
        JsonNode p;
        try {
            env = EnvelopeHelper.parse(rec.value());
            p = om.readTree(env.getPayload());
        } catch (Exception e) {
            log.error("Failed to parse payment.result message: {}", e.getMessage());
            return;
        }

        String status = p.path("status").asText("UNKNOWN");
        String orderId = p.path("orderId").asText(null);
        String paymentId = p.path("paymentId").asText(null);

        if (orderId == null || orderId.isBlank()) {
            log.error("Missing orderId in payment.result, skipping");
            return;
        }

        if ("CHARGED".equalsIgnoreCase(status)) {
            log.info("Payment successful for orderId={}, requesting inventory reservation.", orderId);

            sagaOrchestrator.onPaymentCompleted(orderId, paymentId);
            sagaOrchestrator.onInventoryRequested(orderId);

            ObjectNode payload = om.createObjectNode().put("orderId", orderId);
            inventoryProducer.sendRequest(orderId, payload.toString());
        } else {
            String failureReason = p.path("reason").asText("Payment declined");
            log.error("Payment failed for orderId={}, reason={}", orderId, failureReason);

            sagaOrchestrator.onPaymentFailed(orderId, failureReason);
            ordersFailed.increment();
        }
    }
}
