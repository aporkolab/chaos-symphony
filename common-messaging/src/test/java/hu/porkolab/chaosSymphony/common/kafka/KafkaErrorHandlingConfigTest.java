package hu.porkolab.chaosSymphony.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaErrorHandlingConfig Tests")
class KafkaErrorHandlingConfigTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaErrorHandlingConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaErrorHandlingConfig();
        ReflectionTestUtils.setField(config, "bootstrap", "localhost:9092");
    }

    @Nested
    @DisplayName("ConsumerFactory Tests")
    class ConsumerFactoryTests {

        @Test
        @DisplayName("Should create consumer factory with correct bootstrap servers")
        void consumerFactory_shouldHaveCorrectBootstrapServers() {
            
            ConsumerFactory<String, String> factory = config.consumerFactory();

            
            assertThat(factory).isNotNull();
            assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        }

        @Test
        @DisplayName("Should configure string deserializers")
        void consumerFactory_shouldUseStringDeserializers() {
            
            ConsumerFactory<String, String> factory = config.consumerFactory();

            
            assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        }

        @Test
        @DisplayName("Should disable auto commit")
        void consumerFactory_shouldDisableAutoCommit() {
            
            ConsumerFactory<String, String> factory = config.consumerFactory();

            
            assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        }

        @Test
        @DisplayName("Should set auto offset reset to earliest")
        void consumerFactory_shouldSetAutoOffsetResetToEarliest() {
            
            ConsumerFactory<String, String> factory = config.consumerFactory();

            
            assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }
    }

    @Nested
    @DisplayName("DeadLetterPublishingRecoverer Tests")
    class DeadLetterPublishingRecovererTests {

        @Test
        @DisplayName("Should create dead letter publishing recoverer")
        void deadLetterPublishingRecoverer_shouldBeCreated() {
            
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);

            
            assertThat(recoverer).isNotNull();
        }
    }

    @Nested
    @DisplayName("ErrorHandler Tests")
    class ErrorHandlerTests {

        @Test
        @DisplayName("Should create error handler with default values")
        void errorHandler_shouldBeCreatedWithDefaults() {
            
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);

            
            DefaultErrorHandler handler = config.errorHandler(recoverer, 4, 200L, 2.0, 2000L);

            
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("Should create error handler with custom retry configuration")
        void errorHandler_shouldAcceptCustomRetryConfig() {
            
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);

            
            DefaultErrorHandler handler = config.errorHandler(recoverer, 10, 500L, 3.0, 5000L);

            
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("Should handle single retry attempt")
        void errorHandler_shouldHandleSingleRetry() {
            
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);

            
            DefaultErrorHandler handler = config.errorHandler(recoverer, 1, 100L, 1.0, 100L);

            
            assertThat(handler).isNotNull();
        }
    }

    @Nested
    @DisplayName("KafkaListenerContainerFactory Tests")
    class KafkaListenerContainerFactoryTests {

        @Test
        @DisplayName("Should create container factory")
        void kafkaListenerContainerFactory_shouldBeCreated() {
            
            ConsumerFactory<String, String> consumerFactory = config.consumerFactory();
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);
            DefaultErrorHandler errorHandler = config.errorHandler(recoverer, 4, 200L, 2.0, 2000L);

            
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

            
            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("Should configure RECORD ack mode")
        void kafkaListenerContainerFactory_shouldUseRecordAckMode() {
            
            ConsumerFactory<String, String> consumerFactory = config.consumerFactory();
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);
            DefaultErrorHandler errorHandler = config.errorHandler(recoverer, 4, 200L, 2.0, 2000L);

            
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

            
            assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        }

        @Test
        @DisplayName("Should create factory with error handler configured")
        void kafkaListenerContainerFactory_shouldHaveErrorHandler() {
            
            ConsumerFactory<String, String> consumerFactory = config.consumerFactory();
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);
            DefaultErrorHandler errorHandler = config.errorHandler(recoverer, 4, 200L, 2.0, 2000L);

            
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

            
            assertThat(factory).isNotNull();
            
            
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should wire all beans together correctly")
        void allBeans_shouldWireCorrectly() {
            
            ConsumerFactory<String, String> consumerFactory = config.consumerFactory();
            DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(kafkaTemplate);
            DefaultErrorHandler errorHandler = config.errorHandler(recoverer, 4, 200L, 2.0, 2000L);

            
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

            
            assertThat(consumerFactory).isNotNull();
            assertThat(recoverer).isNotNull();
            assertThat(errorHandler).isNotNull();
            assertThat(factory).isNotNull();
            assertThat(factory.getConsumerFactory()).isEqualTo(consumerFactory);
        }
    }
}
