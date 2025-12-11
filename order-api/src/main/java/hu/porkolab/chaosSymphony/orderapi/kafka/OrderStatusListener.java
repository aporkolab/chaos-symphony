package hu.porkolab.chaosSymphony.orderapi.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusListener {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.status.update", groupId = "order-api-status")
    @Transactional
    public void onStatusUpdate(ConsumerRecord<String, String> record) {
        try {
            JsonNode msg = objectMapper.readTree(record.value());
            String orderId = msg.path("orderId").asText(null);
            String status = msg.path("status").asText(null);
            String reason = msg.path("reason").asText(null);

            if (orderId == null || status == null) {
                log.error("Invalid status update message: orderId={}, status={}", orderId, status);
                return;
            }

            log.info("Received order status update: orderId={}, status={}", orderId, status);

            UUID uuid;
            try {
                uuid = UUID.fromString(orderId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID format for orderId: {}", orderId);
                return;
            }
            orderRepository.findById(uuid).ifPresentOrElse(
                order -> {
                    OrderStatus newStatus = mapStatus(status);
                    order.setStatus(newStatus);
                    if (reason != null && !reason.isEmpty()) {
                        order.setReviewReason(reason);
                    }
                    orderRepository.save(order);
                    log.info("Order {} status updated to {}", orderId, newStatus);
                },
                () -> log.warn("Order not found for status update: {}", orderId)
            );
        } catch (Exception e) {
            log.error("Error processing order status update: {}", e.getMessage(), e);
        }
    }

    private OrderStatus mapStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> OrderStatus.COMPLETED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "PAYMENT_FAILED" -> OrderStatus.PAYMENT_FAILED;
            case "INVENTORY_FAILED" -> OrderStatus.INVENTORY_FAILED;
            case "SHIPPING_FAILED" -> OrderStatus.SHIPPING_FAILED;
            default -> {
                log.warn("Unknown status '{}', defaulting to FAILED", status);
                yield OrderStatus.FAILED;
            }
        };
    }
}
