package hu.porkolab.chaosSymphony.orderapi.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;

import java.util.UUID;


@Slf4j
@Service
@Import({
    hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class,
    hu.porkolab.chaosSymphony.common.idemp.IdempotencyConfig.class
})
public class OrderCancellationListener {

    private static final String COMPENSATION_RESULT_TOPIC = "compensation.result";

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderCancellationListener(
            OrderRepository orderRepository,
            ObjectMapper objectMapper,
            IdempotencyStore idempotencyStore,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "order.cancel", groupId = "order-api-cancel")
    @Transactional
    public void onOrderCancel(ConsumerRecord<String, String> record) {
        String idempotencyKey = "order.cancel:" + record.key();
        
        if (!idempotencyStore.markIfFirst(idempotencyKey)) {
            log.warn("Duplicate order cancellation request, skipping: {}", record.key());
            return;
        }

        try {
            EventEnvelope envelope = EnvelopeHelper.parse(record.value());
            JsonNode msg = objectMapper.readTree(envelope.getPayload());
            
            String orderId = msg.path("orderId").asText(null);
            String reason = msg.path("reason").asText("Saga compensation");

            if (orderId == null || orderId.isBlank()) {
                log.error("Invalid order cancellation request: missing orderId");
                return;
            }

            log.info("Processing order cancellation: orderId={}, reason={}", orderId, reason);

            UUID uuid;
            try {
                uuid = UUID.fromString(orderId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID format for orderId: {}", orderId);
                sendCompensationResult(orderId, "ORDER_CANCEL", false);
                return;
            }
            orderRepository.findById(uuid).ifPresentOrElse(
                order -> {
                    if (!order.getStatus().isTerminal()) {
                        order.setStatus(OrderStatus.CANCELLED);
                        order.setReviewReason("Cancelled: " + reason);
                        orderRepository.save(order);
                        log.info("Order {} cancelled successfully", orderId);
                    } else {
                        log.warn("Order {} already in terminal state: {}, skipping cancellation", 
                                orderId, order.getStatus());
                    }
                },
                () -> log.warn("Order not found for cancellation: {}", orderId)
            );

            
            sendCompensationResult(orderId, "ORDER_CANCEL", true);

        } catch (Exception e) {
            log.error("Error processing order cancellation for key {}: {}", record.key(), e.getMessage(), e);
            
            try {
                String orderId = record.key();
                sendCompensationResult(orderId, "ORDER_CANCEL", false);
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
                    .put("service", "order-api")
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
