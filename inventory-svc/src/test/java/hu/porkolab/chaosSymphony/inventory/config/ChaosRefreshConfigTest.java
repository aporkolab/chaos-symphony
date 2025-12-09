package hu.porkolab.chaosSymphony.inventory.config;

import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
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

@SpringBootTest(
    classes = {
        ChaosRefreshConfig.class,
        ChaosRefreshConfigTest.TestOverrides.class
    },
    properties = {
        "spring.profiles.active=test",
        "chaos.refresh-ms=999999"
    }
)
class ChaosRefreshConfigTest {

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
