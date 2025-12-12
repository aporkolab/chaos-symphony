package hu.porkolab.chaosSymphony.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlingConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> tpl) {
        return new DeadLetterPublishingRecoverer(tpl,
                (rec, ex) -> new TopicPartition(rec.topic() + ".dlt", rec.partition()));
    }

@Bean
public DefaultErrorHandler errorHandler(
        DeadLetterPublishingRecoverer dlpr,
        @Value("${kafka.retry.max-attempts:4}") int maxAttempts,
        @Value("${kafka.retry.initial-interval-ms:200}") long initialInterval,
        @Value("${kafka.retry.multiplier:2.0}") double multiplier,
        @Value("${kafka.retry.max-interval-ms:2000}") long maxInterval) {

    var backoff = new ExponentialBackOffWithMaxRetries(maxAttempts);
    backoff.setInitialInterval(initialInterval);
    backoff.setMultiplier(multiplier);
    backoff.setMaxInterval(maxInterval);

    var handler = new DefaultErrorHandler(dlpr, backoff);
    handler.setCommitRecovered(true);

    
    handler.setRetryListeners((rec, ex, attempt) -> {
        org.slf4j.LoggerFactory.getLogger(DefaultErrorHandler.class)
            .warn("[DLT] retry #{} topic={} offset={} key={} cause={}",
                attempt, rec.topic(), rec.offset(), rec.key(), ex.toString());
    });

    return handler;
}

@Bean(name = "kafkaListenerContainerFactory")
@ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        ConsumerFactory<String, String> cf,
        DefaultErrorHandler errorHandler) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(cf);
    factory.setCommonErrorHandler(errorHandler);

    
    factory.getContainerProperties()
           .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);

    
    

    return factory;
}

}
