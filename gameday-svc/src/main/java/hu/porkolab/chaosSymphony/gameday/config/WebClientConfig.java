package hu.porkolab.chaosSymphony.gameday.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${chaos.svc.url:http://chaos-svc:8088}")
    private String chaosSvcUrl;

    @Bean
    public WebClient chaosSvcWebClient() {
        return WebClient.builder()
                .baseUrl(chaosSvcUrl)
                .build();
    }
}
