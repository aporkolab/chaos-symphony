package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
public class PaymentRefundListener {

    private static final String COMPENSATION_RESULT_TOPIC = "compensation.result";

    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final PaymentStatusStore paymentStatusStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter refundsProcessed;

    public PaymentRefundListener(
            ObjectMapper objectMapper,
            IdempotencyStore idempotencyStore,
            PaymentStatusStore paymentStatusStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.paymentStatusStore = paymentStatusStore;
        this.kafkaTemplate = kafkaTemplate;
        this.refundsProcessed = meterRegistry.counter("payment.refunds.processed");
    }

    @KafkaListener(topics = "payment.refund", groupId = "payment-svc-refund")
    @Transactional
    public void onPaymentRefund(ConsumerRecord<String, String> record) {
        String idempotencyKey = "payment.refund:" + record.key();
        
        if (!idempotencyStore.markIfFirst(idempotencyKey)) {
            log.warn("Duplicate payment refund request, skipping: {}", record.key());
            return;
        }

        try {
            EventEnvelope envelope = EnvelopeHelper.parse(record.value());
            JsonNode msg = objectMapper.readTree(envelope.getPayload());
            
            String orderId = msg.path("orderId").asText(null);
            String paymentId = msg.path("paymentId").asText(null);
            String reason = msg.path("reason").asText("Saga compensation");

            if (orderId == null || orderId.isBlank()) {
                log.error("Invalid payment refund request: missing orderId");
                return;
            }

            log.info("Processing payment refund: orderId={}, paymentId={}, reason={}", 
                    orderId, paymentId, reason);

            
            String currentStatus = paymentStatusStore.getStatus(orderId).orElse("UNKNOWN");
            
            if ("CHARGED".equals(currentStatus)) {
                
                paymentStatusStore.save(orderId, "REFUNDED");
                refundsProcessed.increment();
                log.info("Payment {} refunded successfully for order {}", paymentId, orderId);
            } else if ("REFUNDED".equals(currentStatus)) {
                log.warn("Payment for order {} already refunded, skipping", orderId);
            } else {
                log.warn("Cannot refund payment for order {}: current status is {}", 
                        orderId, currentStatus);
            }

            
            sendCompensationResult(orderId, "PAYMENT_REFUND", true);

        } catch (Exception e) {
            log.error("Error processing payment refund for key {}: {}", record.key(), e.getMessage(), e);
            
            try {
                String orderId = record.key();
                sendCompensationResult(orderId, "PAYMENT_REFUND", false);
            } catch (Exception sendEx) {
                log.error("Failed to send failure compensation result: {}", sendEx.getMessage());
            }
        }
    }

    private void sendCompensationResult(String orderId, String compensationType, boolean success) {
        try {
            String payload = objectMapper.createObjectNode()
                    .put("orderId", orderId)
                    .put("compensationType", compensationType)
                    .put("success", success)
                    .put("service", "payment-svc")
                    .toString();

            String envelope = EnvelopeHelper.envelope(orderId, "CompensationResult", payload);
            kafkaTemplate.send(COMPENSATION_RESULT_TOPIC, orderId, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send compensation result for orderId={}: {}", orderId, ex.getMessage());
                    } else {
                        log.debug("Compensation result sent for orderId={}, type={}", orderId, compensationType);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to build compensation result for orderId={}: {}", orderId, e.getMessage());
        }
    }
}
