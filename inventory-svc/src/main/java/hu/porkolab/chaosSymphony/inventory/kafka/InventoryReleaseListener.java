package hu.porkolab.chaosSymphony.inventory.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



@Slf4j
@Service
public class InventoryReleaseListener {

    private static final String COMPENSATION_RESULT_TOPIC = "compensation.result";

    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter releasesProcessed;

    
    private final Map<String, String> reservationStore = new ConcurrentHashMap<>();

    public InventoryReleaseListener(
        ObjectMapper objectMapper,
        IdempotencyStore idempotencyStore,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.kafkaTemplate = kafkaTemplate;
        this.releasesProcessed = meterRegistry.counter("inventory.releases.processed");
    }

    @KafkaListener(topics = "inventory.release", groupId = "inventory-svc-release")
    @Transactional
    public void onInventoryRelease(ConsumerRecord<String, String> record) {
        String idempotencyKey = "inventory.release:" + record.key();

        if (!idempotencyStore.markIfFirst(idempotencyKey)) {
            log.warn("Duplicate inventory release request, skipping: {}", record.key());
            return;
        }

        try {
            EventEnvelope envelope = EnvelopeHelper.parse(record.value());
            JsonNode msg = objectMapper.readTree(envelope.getPayload());

            String orderId = msg.path("orderId").asText(null);
            String reservationId = msg.path("reservationId").asText(null);
            String reason = msg.path("reason").asText("Saga compensation");

            if (orderId == null) {
                log.error("Invalid inventory release request: missing orderId");
                return;
            }

            log.info("Processing inventory release: orderId={}, reservationId={}, reason={}",
                orderId, reservationId, reason);

            
            String previousReservation = reservationStore.remove(orderId);

            if (previousReservation != null || reservationId != null) {
                releasesProcessed.increment();
                log.info("Inventory reservation {} released for order {}",
                    reservationId != null ? reservationId : previousReservation, orderId);
            } else {
                log.warn("No reservation found to release for order {}", orderId);
            }

            
            sendCompensationResult(orderId, "INVENTORY_RELEASE", true);

        } catch (Exception e) {
            log.error("Error processing inventory release for key {}: {}", record.key(), e.getMessage(), e);
            
            try {
                String orderId = record.key();
                sendCompensationResult(orderId, "INVENTORY_RELEASE", false);
            } catch (Exception sendEx) {
                log.error("Failed to send failure compensation result: {}", sendEx.getMessage());
            }
        }
    }

    
    public void trackReservation(String orderId, String reservationId) {
        reservationStore.put(orderId, reservationId);
    }

    private void sendCompensationResult(String orderId, String compensationType, boolean success) {
        try {
            String payload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("compensationType", compensationType)
                .put("success", success)
                .put("service", "inventory-svc")
                .toString();

            String envelope = EnvelopeHelper.envelope(orderId, "CompensationResult", payload);
            kafkaTemplate.send(COMPENSATION_RESULT_TOPIC, orderId, envelope);
            log.debug("Compensation result sent for orderId={}, type={}", orderId, compensationType);
        } catch (Exception e) {
            log.error("Failed to send compensation result for orderId={}: {}", orderId, e.getMessage());
        }
    }
}
