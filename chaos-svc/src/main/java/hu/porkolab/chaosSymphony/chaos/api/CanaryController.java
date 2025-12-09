package hu.porkolab.chaosSymphony.chaos.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;


@RestController
@RequestMapping("/api/canary")
@Slf4j
public class CanaryController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String orchestratorEnvUrl;

    public CanaryController(
            ObjectMapper objectMapper,
            @Value("${orchestrator.base-url:http://orchestrator:8091}") String orchestratorBaseUrl) {
        this.objectMapper = objectMapper;
        this.orchestratorEnvUrl = orchestratorBaseUrl + "/actuator/env";
        this.webClient = WebClient.builder()
                .baseUrl(orchestratorBaseUrl)
                .build();
    }

    public record CanaryConfig(boolean enabled, double percentage) {}

    @PostMapping("/config")
    public Mono<Void> configureCanary(@RequestBody CanaryConfig config) {
        log.info("Setting canary mode to enabled={} with percentage={}", config.enabled(), config.percentage());

        double percentageToSet = config.enabled() ? config.percentage() : 0.0;
        Map<String, Object> body = Map.of("name", "canary.payment.percentage", "value", percentageToSet);

        return webClient.post()
                .uri("/actuator/env")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Orchestrator canary config updated successfully."))
                .doOnError(error -> log.error("Failed to update orchestrator canary config: {}", error.getMessage()))
                .then();
    }
}
