package hu.porkolab.chaosSymphony.shipping.config;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MetricsConfig.class,
    MetricsConfigTest.TestConfig.class
})
class MetricsConfigTest {

    @Autowired
    Counter messagesProcessed;

    @Autowired
    Counter dltMessagesTotal;

    @Autowired
    Timer processingTime;

    @Test
    void metricsAreCreated() {
        assertThat(messagesProcessed).isNotNull();
        assertThat(dltMessagesTotal).isNotNull();
        assertThat(processingTime).isNotNull();
    }

    @Configuration
    static class TestConfig {
        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }
}
