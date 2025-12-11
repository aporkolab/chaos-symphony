package hu.porkolab.chaosSymphony.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
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
public class PaymentRequestedListener {

    private final PaymentResultProducer producer;
    private final IdempotencyStore idempotencyStore;
    private final PaymentStatusStore paymentStatusStore;
    private final Counter paymentsProcessedMain;
    private final Counter paymentsProcessedCanary;
    private final Timer processingTime;
    private final ObjectMapper objectMapper;
    
    @Value("${payment.processing.success-rate:0.9}")
    private double successRate;

    public PaymentRequestedListener(
            PaymentResultProducer producer,
            IdempotencyStore idempotencyStore,
            PaymentStatusStore paymentStatusStore,
            Counter paymentsProcessedMain,
            Counter paymentsProcessedCanary,
            Timer processingTime,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.idempotencyStore = idempotencyStore;
        this.paymentStatusStore = paymentStatusStore;
        this.paymentsProcessedMain = paymentsProcessedMain;
        this.paymentsProcessedCanary = paymentsProcessedCanary;
        this.processingTime = processingTime;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, random = true),
            include = {SocketTimeoutException.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = "${kafka.topic.payment.requested}", groupId = "${kafka.group.id.payment}")
    @Transactional
    public void onPaymentRequested(ConsumerRecord<String, String> rec) {
        processPayment(rec, paymentsProcessedMain, false);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, random = true),
            include = {SocketTimeoutException.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = "${kafka.topic.payment.requested.canary}", groupId = "${kafka.group.id.payment.canary}")
    @Transactional
    public void onPaymentRequestedCanary(ConsumerRecord<String, String> rec) {
        processPayment(rec, paymentsProcessedCanary, true);
    }

    
    private void processPayment(ConsumerRecord<String, String> rec, Counter counter, boolean isCanary) {
        String logPrefix = isCanary ? "[CANARY] " : "";
        long startTime = System.nanoTime();
        
        try {
            counter.increment();
            
            if (!idempotencyStore.markIfFirst(rec.key())) {
                log.warn("{}Duplicate message detected, skipping: {}", logPrefix, rec.key());
                return;
            }

            EventEnvelope envelope;
            JsonNode message;
            try {
                envelope = EnvelopeHelper.parse(rec.value());
                message = objectMapper.readTree(envelope.getPayload());
            } catch (Exception e) {
                log.error("{}Failed to parse payment.requested message: {}", logPrefix, e.getMessage());
                return;
            }
            
            String orderId = envelope.getOrderId();
            if (orderId == null || orderId.isBlank()) {
                log.error("{}Missing orderId in payment.requested, skipping", logPrefix);
                return;
            }
            
            double amount = message.path("amount").asDouble();

            simulatePaymentProcessing(orderId, logPrefix);

            String status = "CHARGED";
            String paymentId = java.util.UUID.randomUUID().toString();
            paymentStatusStore.save(orderId, status);

            String resultPayload = objectMapper.createObjectNode()
                    .put("orderId", orderId)
                    .put("paymentId", paymentId)
                    .put("status", status)
                    .put("amount", amount)
                    .toString();

            log.info("{}Payment processed for orderId={}, paymentId={}, status: {}", logPrefix, orderId, paymentId, status);
            producer.sendResult(orderId, resultPayload);
            
        } finally {
            processingTime.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    
    private void simulatePaymentProcessing(String orderId, String logPrefix) {
        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;
        if (!success) {
            throw new IllegalStateException(
                    logPrefix + "Payment processing failed for order: " + orderId);
        }
    }
}
