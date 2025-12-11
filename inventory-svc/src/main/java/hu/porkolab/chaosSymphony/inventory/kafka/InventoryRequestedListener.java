package hu.porkolab.chaosSymphony.inventory.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketTimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class InventoryRequestedListener {

    private final InventoryResultProducer producer;
    private final IdempotencyStore idempotencyStore;
    private final InventoryReleaseListener releaseListener;
    private final Counter messagesProcessed;
    private final Timer processingTime;
    private final ObjectMapper objectMapper;
    
    @Value("${inventory.processing.success-rate:0.95}")
    private double successRate;

    public InventoryRequestedListener(
            InventoryResultProducer producer,
            IdempotencyStore idempotencyStore,
            InventoryReleaseListener releaseListener,
            Counter messagesProcessed,
            Timer processingTime,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.idempotencyStore = idempotencyStore;
        this.releaseListener = releaseListener;
        this.messagesProcessed = messagesProcessed;
        this.processingTime = processingTime;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, random = true),
            include = {SocketTimeoutException.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = "${kafka.topic.inventory.requested}", groupId = "${kafka.group.id.inventory}")
    @Transactional
    public void onInventoryRequested(ConsumerRecord<String, String> rec) {
        long startTime = System.nanoTime();
        try {
            messagesProcessed.increment();
            
            if (!idempotencyStore.markIfFirst(rec.key())) {
                log.warn("Duplicate message detected, skipping: {}", rec.key());
                return;
            }

            EventEnvelope envelope;
            JsonNode message;
            try {
                envelope = EnvelopeHelper.parse(rec.value());
                message = objectMapper.readTree(envelope.getPayload());
            } catch (Exception e) {
                log.error("Failed to parse inventory.requested message: {}", e.getMessage());
                return;
            }

            String orderId = envelope.getOrderId();
            if (orderId == null || orderId.isBlank()) {
                log.error("Missing orderId in inventory.requested, skipping");
                return;
            }

            int items = message.path("items").asInt(1);

            validateAndReserveInventory(orderId, items);

            String status = "RESERVED";
            String reservationId = java.util.UUID.randomUUID().toString();
            
            
            releaseListener.trackReservation(orderId, reservationId);
            
            String resultPayload = objectMapper.createObjectNode()
                    .put("orderId", orderId)
                    .put("reservationId", reservationId)
                    .put("status", status)
                    .put("items", items)
                    .toString();

            log.info("Inventory processed for orderId={}, reservationId={}, items={}, status={}", orderId, reservationId, items, status);
            producer.sendResult(orderId, resultPayload);
            
        } finally {
            processingTime.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    
    private void validateAndReserveInventory(String orderId, int items) {
        if (items <= 0) {
            throw new IllegalArgumentException("Invalid item count for order: " + orderId);
        }
        
        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;
        if (!success) {
            throw new IllegalStateException("Inventory unavailable for order: " + orderId);
        }
    }
}
