package hu.porkolab.chaosSymphony.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractIdempotentListener Tests")
class AbstractIdempotentListenerTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private TestListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        listener = new TestListener(idempotencyStore, meterRegistry, objectMapper, "test-service");
    }

    @Nested
    @DisplayName("Message Handling Tests")
    class MessageHandlingTests {

        @Test
        @DisplayName("Should process new message successfully")
        void handleMessage_newMessage_shouldProcess() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String payload = "{\"amount\": 100}";
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", payload);
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            assertThat(listener.getProcessedCount()).isEqualTo(1);
            assertThat(listener.getLastEnvelope()).isNotNull();
            assertThat(listener.getLastEnvelope().getOrderId()).isEqualTo("order-123");
            assertThat(listener.getLastEnvelope().getType()).isEqualTo("TestEvent");
        }

        @Test
        @DisplayName("Should skip duplicate message")
        void handleMessage_duplicateMessage_shouldSkip() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(false);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            assertThat(listener.getProcessedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw exception on processing error")
        void handleMessage_processingError_shouldThrow() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);
            listener.setThrowOnProcess(true);

            
            assertThatThrownBy(() -> listener.handleMessage(record))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Message processing failed");
        }

        @Test
        @DisplayName("Should handle null key gracefully")
        void handleMessage_nullKey_shouldHandleGracefully() {
            
            when(idempotencyStore.markIfFirst(null)).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, null, envelope);

            
            listener.handleMessage(record);

            
            assertThat(listener.getProcessedCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metrics Tests")
    class MetricsTests {

        @Test
        @DisplayName("Should increment received counter for every message")
        void handleMessage_anyMessage_shouldIncrementReceived() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            Counter received = meterRegistry.get("kafka.messages.received")
                    .tag("service", "test-service")
                    .counter();
            assertThat(received.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should increment duplicate counter for duplicate messages")
        void handleMessage_duplicateMessage_shouldIncrementDuplicate() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(false);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            Counter duplicate = meterRegistry.get("kafka.messages.duplicate")
                    .tag("service", "test-service")
                    .counter();
            assertThat(duplicate.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should increment processed counter for successful messages")
        void handleMessage_successfulMessage_shouldIncrementProcessed() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            Counter processed = meterRegistry.get("kafka.messages.processed")
                    .tag("service", "test-service")
                    .counter();
            assertThat(processed.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should increment failed counter on exception")
        void handleMessage_failedMessage_shouldIncrementFailed() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);
            listener.setThrowOnProcess(true);

            
            try {
                listener.handleMessage(record);
            } catch (RuntimeException ignored) {
            }

            
            Counter failed = meterRegistry.get("kafka.messages.failed")
                    .tag("service", "test-service")
                    .counter();
            assertThat(failed.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should record processing time")
        void handleMessage_anyMessage_shouldRecordTime() {
            
            when(idempotencyStore.markIfFirst(anyString())).thenReturn(true);
            String envelope = EnvelopeHelper.envelope("order-123", "event-456", "TestEvent", "{}");
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "event-456", envelope);

            
            listener.handleMessage(record);

            
            Timer timer = meterRegistry.get("kafka.message.processing.time")
                    .tag("service", "test-service")
                    .timer();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Payload Parsing Tests")
    class PayloadParsingTests {

        @Test
        @DisplayName("Should parse JSON payload to target class")
        void parsePayload_validJson_shouldParse() {
            
            String json = "{\"amount\": 100, \"currency\": \"USD\"}";

            
            TestPayload payload = listener.parsePayload(json, TestPayload.class);

            
            assertThat(payload.amount).isEqualTo(100);
            assertThat(payload.currency).isEqualTo("USD");
        }

        @Test
        @DisplayName("Should throw on invalid JSON")
        void parsePayload_invalidJson_shouldThrow() {
            
            String invalidJson = "not valid json";

            
            assertThatThrownBy(() -> listener.parsePayload(invalidJson, TestPayload.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to parse payload");
        }
    }

    @Nested
    @DisplayName("Timer Access Tests")
    class TimerAccessTests {

        @Test
        @DisplayName("Should return processing time timer")
        void getProcessingTime_shouldReturnTimer() {
            
            Timer timer = listener.getProcessingTime();

            
            assertThat(timer).isNotNull();
        }
    }

    
    static class TestListener extends AbstractIdempotentListener {
        private final AtomicInteger processedCount = new AtomicInteger(0);
        private final AtomicReference<EventEnvelope> lastEnvelope = new AtomicReference<>();
        private volatile boolean throwOnProcess = false;

        protected TestListener(IdempotencyStore store, MeterRegistry registry, ObjectMapper mapper, String name) {
            super(store, registry, mapper, name);
        }

        @Override
        protected void processMessage(EventEnvelope envelope, String key) throws Exception {
            if (throwOnProcess) {
                throw new RuntimeException("Test processing error");
            }
            processedCount.incrementAndGet();
            lastEnvelope.set(envelope);
        }

        
        @Override
        public void handleMessage(ConsumerRecord<String, String> record) {
            super.handleMessage(record);
        }

        
        @Override
        public <T> T parsePayload(String payload, Class<T> clazz) {
            return super.parsePayload(payload, clazz);
        }

        
        @Override
        public Timer getProcessingTime() {
            return super.getProcessingTime();
        }

        int getProcessedCount() {
            return processedCount.get();
        }

        EventEnvelope getLastEnvelope() {
            return lastEnvelope.get();
        }

        void setThrowOnProcess(boolean throwOnProcess) {
            this.throwOnProcess = throwOnProcess;
        }
    }

    
    static class TestPayload {
        public int amount;
        public String currency;
    }
}
