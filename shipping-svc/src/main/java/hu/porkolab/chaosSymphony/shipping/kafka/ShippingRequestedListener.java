package hu.porkolab.chaosSymphony.shipping.kafka;

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
public class ShippingRequestedListener {

    private final ShippingResultProducer producer;
    private final IdempotencyStore idempotencyStore;
    private final Counter messagesProcessed;
    private final Timer processingTime;
    private final ObjectMapper objectMapper;
    
    @Value("${shipping.processing.success-rate:0.98}")
    private double successRate;

    public ShippingRequestedListener(
            ShippingResultProducer producer,
            IdempotencyStore idempotencyStore,
            Counter messagesProcessed,
            Timer processingTime,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.idempotencyStore = idempotencyStore;
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
    @KafkaListener(topics = "${kafka.topic.shipping.requested}", groupId = "${kafka.group.id.shipping}")
    @Transactional
    public void onShippingRequested(ConsumerRecord<String, String> rec) {
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
                log.error("Failed to parse shipping.requested message: {}", e.getMessage());
                return;
            }

            String orderId = envelope.getOrderId();
            if (orderId == null || orderId.isBlank()) {
                log.error("Missing orderId in shipping.requested, skipping");
                return;
            }

            String address = message.path("address").asText();

            validateAndShip(orderId, address);

            String status = "SHIPPED";
            String shippingId = java.util.UUID.randomUUID().toString();
            String resultPayload = objectMapper.createObjectNode()
                    .put("orderId", orderId)
                    .put("shippingId", shippingId)
                    .put("status", status)
                    .put("address", address)
                    .toString();

            log.info("Shipping processed for orderId={}, shippingId={}, address={}, status={}", orderId, shippingId, address, status);
            producer.sendResult(orderId, resultPayload);
            
        } finally {
            processingTime.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    
    private void validateAndShip(String orderId, String address) {
        if (address == null || address.isBlank() || "UNKNOWN".equals(address)) {
            throw new IllegalArgumentException("Invalid shipping address for order: " + orderId);
        }
        
        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;
        if (!success) {
            throw new IllegalStateException("Shipping carrier unavailable for order: " + orderId);
        }
    }
}
