package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationProducer {

    private static final String PAYMENT_REFUND_TOPIC = "payment.refund";
    private static final String INVENTORY_RELEASE_TOPIC = "inventory.release";
    private static final String ORDER_CANCEL_TOPIC = "order.cancel";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public void requestPaymentRefund(String orderId, String paymentId, String reason) {
        log.warn("Requesting payment refund for orderId={}, paymentId={}, reason={}",
            orderId, paymentId, reason);

        ObjectNode payload = objectMapper.createObjectNode()
            .put("orderId", orderId)
            .put("paymentId", paymentId)
            .put("reason", reason)
            .put("compensationType", "REFUND");

        String envelope = EnvelopeHelper.envelope(
            orderId,
            UUID.randomUUID().toString(),
            "PaymentRefundRequested",
            payload.toString()
        );

        kafkaTemplate.send(PAYMENT_REFUND_TOPIC, orderId, envelope)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to send payment refund request for orderId={}: {}",
                        orderId, ex.getMessage(), ex);
                } else {
                    log.debug("Payment refund request sent for orderId={}", orderId);
                }
            });
    }


    public void requestInventoryRelease(String orderId, String reservationId, String reason) {
        log.warn("Requesting inventory release for orderId={}, reservationId={}, reason={}",
            orderId, reservationId, reason);

        ObjectNode payload = objectMapper.createObjectNode()
            .put("orderId", orderId)
            .put("reservationId", reservationId)
            .put("reason", reason)
            .put("compensationType", "RELEASE");

        String envelope = EnvelopeHelper.envelope(
            orderId,
            UUID.randomUUID().toString(),
            "InventoryReleaseRequested",
            payload.toString()
        );

        kafkaTemplate.send(INVENTORY_RELEASE_TOPIC, orderId, envelope)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to send inventory release request for orderId={}: {}",
                        orderId, ex.getMessage(), ex);
                } else {
                    log.debug("Inventory release request sent for orderId={}", orderId);
                }
            });
    }


    public void requestOrderCancellation(String orderId, String reason) {
        log.warn("Requesting order cancellation for orderId={}, reason={}", orderId, reason);

        ObjectNode payload = objectMapper.createObjectNode()
            .put("orderId", orderId)
            .put("reason", reason)
            .put("compensationType", "CANCEL");

        String envelope = EnvelopeHelper.envelope(
            orderId,
            UUID.randomUUID().toString(),
            "OrderCancellationRequested",
            payload.toString()
        );

        kafkaTemplate.send(ORDER_CANCEL_TOPIC, orderId, envelope)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to send order cancellation request for orderId={}: {}",
                        orderId, ex.getMessage(), ex);
                } else {
                    log.debug("Order cancellation request sent for orderId={}", orderId);
                }
            });
    }
}
