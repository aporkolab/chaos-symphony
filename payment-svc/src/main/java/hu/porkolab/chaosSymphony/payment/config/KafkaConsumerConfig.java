package hu.porkolab.chaosSymphony.payment.config;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter dltMessagesTotal;

    @Value("${spring.kafka.retry.max-attempts}")
    private long maxAttempts;

    @Value("${spring.kafka.retry.initial-interval-ms}")
    private long initialInterval;

    @Value("${kafka.topic.payment.dlt}")
    private String dltTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (rec, ex) -> {
                    System.out.println("Message going to DLT: " + rec.topic());
                    dltMessagesTotal.increment();
                    return new org.apache.kafka.common.TopicPartition(dltTopic, rec.partition());
                });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(initialInterval, maxAttempts)
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
