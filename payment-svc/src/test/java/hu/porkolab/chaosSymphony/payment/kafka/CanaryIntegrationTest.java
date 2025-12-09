package hu.porkolab.chaosSymphony.payment.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.AutoConfigureDataRedis;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureDataRedis
@EmbeddedKafka(partitions = 1, topics = { "payment.requested", "payment.requested.canary" })
@Disabled("Replaced by unit tests for PaymentRequestedListener")
class CanaryIntegrationTest {

    @TestConfiguration
    static class Config implements KafkaListenerConfigurer {

        @Bean
        public Counter paymentsProcessedMain() { return mock(Counter.class); }

        @Bean
        public Counter paymentsProcessedCanary() { return mock(Counter.class); }

        @Bean
        public Timer processingTime() { return mock(Timer.class); }

        @Bean
        public IdempotencyStore idempotencyStore() {
            IdempotencyStore store = mock(IdempotencyStore.class);
            when(store.markIfFirst(anyString())).thenReturn(true);
            return store;
        }

        @Bean
        public PaymentStatusStore paymentStatusStore() {
            return mock(PaymentStatusStore.class);
        }

        @Bean
        public PaymentResultProducer paymentResultProducer() {
            return mock(PaymentResultProducer.class);
        }

        @Bean
        public PaymentRequestedListener paymentRequestedListener(
            PaymentResultProducer producer,
            IdempotencyStore idempotencyStore,
            PaymentStatusStore paymentStatusStore,
            Counter paymentsProcessedMain,
            Counter paymentsProcessedCanary,
            Timer processingTime) {

            return new PaymentRequestedListener(
                producer,
                idempotencyStore,
                paymentStatusStore,
                paymentsProcessedMain,
                paymentsProcessedCanary,
                processingTime,
                new com.fasterxml.jackson.databind.ObjectMapper()
            );
        }

        @Override
        public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) { }
    }

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private Counter paymentsProcessedMain;

    @Autowired
    private Counter paymentsProcessedCanary;

    @Test
    void testMainAndCanaryConsumers() {

        template.send("payment.requested",
            "order1",
            EnvelopeHelper.envelope("order1", "PaymentRequested",
                "{\"orderId\":\"order1\",\"amount\":100.0}"));

        await().atMost(5, SECONDS).untilAsserted(() ->
            verify(paymentsProcessedMain, atLeastOnce()).increment()
        );

        template.send("payment.requested.canary",
            "order2",
            EnvelopeHelper.envelope("order2", "PaymentRequested",
                "{\"orderId\":\"order2\",\"amount\":200.0}"));

        await().atMost(5, SECONDS).untilAsserted(() ->
            verify(paymentsProcessedCanary, atLeastOnce()).increment()
        );
    }
}
