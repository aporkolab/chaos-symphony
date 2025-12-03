package hu.porkolab.chaosSymphony.chaos.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;


@RestController
@RequestMapping("/api/canary")
@Slf4j
public class CanaryController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CanaryController(
            ObjectMapper objectMapper,
            @Value("${orchestrator.management-url:http://orchestrator:9091}") String orchestratorManagementUrl) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(orchestratorManagementUrl)
                .build();
    }

    public record CanaryConfig(boolean enabled, double percentage) {}

    @GetMapping("/config")
    public Mono<CanaryConfig> getCanaryConfig() {
        return webClient.get()
                .uri("/actuator/env/canary.payment.percentage")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode propNode = root.path("property").path("value");
                        double percentage = propNode.isMissingNode() ? 0.0 : propNode.asDouble(0.0);
                        return new CanaryConfig(percentage > 0, percentage);
                    } catch (Exception e) {
                        log.warn("Failed to parse canary config response: {}", e.getMessage());
                        return new CanaryConfig(false, 0.0);
                    }
                })
                .onErrorReturn(new CanaryConfig(false, 0.0));
    }

    @PostMapping("/config")
    public Mono<CanaryConfig> configureCanary(@RequestBody CanaryConfig config) {
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
                .thenReturn(new CanaryConfig(config.enabled(), percentageToSet));
    }
}
