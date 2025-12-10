package hu.porkolab.chaosSymphony.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;


@Slf4j
public abstract class AbstractIdempotentListener {

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    private final Counter receivedCounter;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Timer processingTime;

    
    protected AbstractIdempotentListener(
        IdempotencyStore idempotencyStore,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper,
        String serviceName) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;

        this.receivedCounter = Counter.builder("kafka.messages.received")
            .tag("service", serviceName)
            .description("Total messages received")
            .register(meterRegistry);

        this.processedCounter = Counter.builder("kafka.messages.processed")
            .tag("service", serviceName)
            .description("Successfully processed messages")
            .register(meterRegistry);

        this.duplicateCounter = Counter.builder("kafka.messages.duplicate")
            .tag("service", serviceName)
            .description("Duplicate messages skipped")
            .register(meterRegistry);

        this.failedCounter = Counter.builder("kafka.messages.failed")
            .tag("service", serviceName)
            .description("Failed message processing attempts")
            .register(meterRegistry);

        this.processingTime = Timer.builder("kafka.message.processing.time")
            .tag("service", serviceName)
            .description("Message processing duration")
            .register(meterRegistry);
    }

    
    public void handleMessage(ConsumerRecord<String, String> record) {
        receivedCounter.increment();
        String key = record.key();

        
        if (!idempotencyStore.markIfFirst(key)) {
            log.debug("Duplicate message detected, key={}", key);
            duplicateCounter.increment();
            return;
        }

        Timer.Sample sample = Timer.start();
        try {
            EventEnvelope envelope = EnvelopeHelper.parse(record.value());
            processMessage(envelope, key);
            processedCounter.increment();
            log.debug("Message processed successfully, key={}, type={}", key, envelope.getType());
        } catch (Exception e) {
            failedCounter.increment();
            log.error("Message processing failed, key={}", key, e);
            throw new RuntimeException("Message processing failed for key=" + key, e);
        } finally {
            sample.stop(processingTime);
        }
    }

    
    protected abstract void processMessage(EventEnvelope envelope, String key) throws Exception;

    
    protected <T> T parsePayload(String payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payload to " + clazz.getSimpleName(), e);
        }
    }

    
    protected Timer getProcessingTime() {
        return processingTime;
    }

    
    protected String getServiceName() {
        return serviceName;
    }
}
