package hu.porkolab.chaosSymphony.shipping.config;

import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    ChaosRefreshConfig.class,
    ChaosRefreshConfigTest.TestConfig.class
})
class ChaosRefreshConfigTest {

    @Autowired
    Supplier<ChaosRules> chaosRulesSupplier;

    @Autowired
    ChaosProducer chaosProducer;

    @Autowired
    ChaosRefreshConfig config;

    @Test
    void contextLoads_andBeansExist() {
        assertThat(chaosRulesSupplier).isNotNull();
        assertThat(chaosRulesSupplier.get()).isNotNull();
        assertThat(chaosProducer).isNotNull();
        assertThat(config).isNotNull();
    }

    @Test
    void refresh_doesNotThrow_whenRemoteIsUnavailable() {
        config.refresh(); 
        assertThat(chaosRulesSupplier.get()).isNotNull();
    }

    @Configuration
    static class TestConfig {

        @Bean
        RestClient.Builder builder() {
            return RestClient.builder();
        }

        @Bean
        public ChaosProducer chaosProducer() {
            return Mockito.mock(ChaosProducer.class);
        }
    }
}
