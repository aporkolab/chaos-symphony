package hu.porkolab.chaosSymphony.gameday.service;

import hu.porkolab.chaosSymphony.gameday.model.ChaosRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChaosSvcClient {

    private final WebClient chaosSvcWebClient;

    public Mono<ChaosRule> createChaosRule(ChaosRule rule) {
        return chaosSvcWebClient.post()
                .uri("/api/chaos/rules")
                .bodyValue(rule)
                .retrieve()
                .bodyToMono(ChaosRule.class);
    }

    public Mono<Void> deleteChaosRule(String ruleId) {
        return chaosSvcWebClient.delete()
                .uri("/api/chaos/rules/{id}", ruleId)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
