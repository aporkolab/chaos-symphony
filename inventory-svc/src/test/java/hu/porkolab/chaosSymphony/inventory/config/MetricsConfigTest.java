package hu.porkolab.chaosSymphony.inventory.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {
        MetricsConfig.class,
        MetricsConfigTest.TestOverrides.class
    },
    properties = "spring.profiles.active=test"
)
class MetricsConfigTest {

    @Autowired
    MetricsConfig config;

    @TestConfiguration
    static class TestOverrides {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void metricsBeansLoad() {
        assertThat(config).isNotNull();
    }
}
