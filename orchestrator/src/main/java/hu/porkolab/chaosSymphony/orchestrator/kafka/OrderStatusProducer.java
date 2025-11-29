package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendStatusUpdate(String orderId, String status, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "status", status,
                "reason", reason != null ? reason : ""
            ));
            kafkaTemplate.send("order.status.update", orderId, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send order status update for {}: {}", orderId, ex.getMessage());
                    } else {
                        log.info("Order status update sent: orderId={}, status={}", orderId, status);
                    }
                });
        } catch (Exception e) {
            log.error("Error serializing order status update for {}: {}", orderId, e.getMessage());
        }
    }
}
