package hu.porkolab.chaosSymphony.shipping.config;

import io.micrometer.core.instrument.Counter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    KafkaConsumerConfig.class,
    KafkaConsumerConfigTest.TestConfig.class
})
@TestPropertySource(properties = {
    "spring.kafka.retry.max-attempts=3",
    "spring.kafka.retry.initial-interval-ms=500",
    "spring.kafka.topic.shipping.dlt=shipping.dlt"
})
class KafkaConsumerConfigTest {

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<?, ?> factory;

    @Test
    void factoryLoads() {
        assertThat(factory).isNotNull();
        assertThat(factory.getConsumerFactory()).isNotNull();
        assertThat(factory.getContainerProperties()).isNotNull();
    }

    @Configuration
    static class TestConfig {

        @Bean
        public KafkaTemplate<String, String> kafkaTemplate() {
            return Mockito.mock(KafkaTemplate.class);
        }

        @Bean
        public Counter dltMessagesTotal() {
            return Mockito.mock(Counter.class);
        }

        @Bean
        public ConsumerFactory<String, String> consumerFactory() {
            return new DefaultKafkaConsumerFactory<>(
                Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.GROUP_ID_CONFIG, "test-group"
                )
            );
        }
    }
}
