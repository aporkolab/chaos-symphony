package hu.porkolab.chaosSymphony.streams.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${prometheus.url:http://prometheus:9090}")
    private String prometheusUrl;

    @Bean
    public WebClient prometheusWebClient() {
        return WebClient.builder()
                .baseUrl(prometheusUrl)
                .build();
    }
}
