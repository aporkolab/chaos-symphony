package hu.porkolab.chaosSymphony.inventory.config;

import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChaosRefreshConfigTest {

    @Nested
    @SpringBootTest(
        classes = {
            ChaosRefreshConfig.class,
            SpringBootTests.TestOverrides.class
        },
        properties = {
            "spring.profiles.active=test",
            "chaos.refresh-ms=999999"
        }
    )
    class SpringBootTests {

        @Autowired
        ChaosRefreshConfig config;

        @Autowired
        Supplier<ChaosRules> rulesSupplier;

        @Autowired
        ChaosProducer producer;

        @TestConfiguration
        static class TestOverrides {

            @Bean
            RestClient.Builder restClientBuilder() {
                return RestClient.builder();
            }

            @Bean
            KafkaTemplate<String, String> kafkaTemplate() {
                return mock(KafkaTemplate.class);
            }
        }

        @Test
        void contextLoads() {
            assertThat(config).isNotNull();
        }

        @Test
        void supplierExists() {
            assertThat(rulesSupplier).isNotNull();
            assertThat(rulesSupplier.get()).isNotNull();
        }

        @Test
        void producerExists() {
            assertThat(producer).isNotNull();
        }
    }

    @Nested
    @DisplayName("Unit Tests")
    class UnitTests {

        @Test
        @DisplayName("Should create config with custom CHAOS_URL")
        void shouldCreateConfigWithCustomUrl() {
            System.setProperty("chaos.url", "http://custom:9999");
            try {
                RestClient.Builder builder = RestClient.builder();
                ChaosRefreshConfig config = new ChaosRefreshConfig(builder);
                assertThat(config).isNotNull();
            } finally {
                System.clearProperty("chaos.url");
            }
        }

        @Test
        @DisplayName("Should return supplier that provides ChaosRules")
        void shouldReturnSupplierThatProvidesChaosRules() {
            RestClient.Builder builder = RestClient.builder();
            ChaosRefreshConfig config = new ChaosRefreshConfig(builder);

            Supplier<ChaosRules> supplier = config.chaosRulesSupplier();

            assertThat(supplier).isNotNull();
            assertThat(supplier.get()).isNotNull();
        }

        @Test
        @DisplayName("Should handle refresh failure gracefully")
        void shouldHandleRefreshFailureGracefully() {
            RestClient.Builder builder = RestClient.builder();
            ChaosRefreshConfig config = new ChaosRefreshConfig(builder);

            
            config.refresh();

            
            assertThat(config.chaosRulesSupplier().get()).isNotNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("Should create ChaosProducer bean")
        void shouldCreateChaosProducerBean() {
            RestClient.Builder builder = RestClient.builder();
            ChaosRefreshConfig config = new ChaosRefreshConfig(builder);
            KafkaTemplate<String, String> mockTemplate = mock(KafkaTemplate.class);

            ChaosProducer producer = config.chaosProducer(mockTemplate, config.chaosRulesSupplier());

            assertThat(producer).isNotNull();
        }
    }
}
