package hu.porkolab.chaosSymphony.gameday.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientConfigTest {

    @Test
    void shouldCreateWebClientWithDefaultUrl() throws Exception {
        WebClientConfig config = new WebClientConfig();

        // Set default URL via reflection
        Field urlField = WebClientConfig.class.getDeclaredField("chaosSvcUrl");
        urlField.setAccessible(true);
        urlField.set(config, "http://chaos-svc:8088");

        WebClient webClient = config.chaosSvcWebClient();

        assertThat(webClient).isNotNull();
    }

    @Test
    void shouldCreateWebClientWithCustomUrl() throws Exception {
        WebClientConfig config = new WebClientConfig();

        Field urlField = WebClientConfig.class.getDeclaredField("chaosSvcUrl");
        urlField.setAccessible(true);
        urlField.set(config, "http://localhost:9999");

        WebClient webClient = config.chaosSvcWebClient();

        assertThat(webClient).isNotNull();
    }
}
