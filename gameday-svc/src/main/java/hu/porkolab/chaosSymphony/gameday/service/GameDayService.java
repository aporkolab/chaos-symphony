package hu.porkolab.chaosSymphony.gameday.service;

import hu.porkolab.chaosSymphony.gameday.model.ChaosRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameDayService {

    private final ChaosSvcClient chaosSvcClient;

    @Async
    public void runGameDay() {
        log.info("--- Starting GameDay Scenario ---");
        List<ChaosRule> activeRules = new ArrayList<>();

        try {
            // Step 1: Smoke Test (Simulated)
            log.info("[Gameday Step 1/5] Running smoke tests...");
            Thread.sleep(2000); // Simulate duration

            // Step 2: Chaos ON
            log.info("[Gameday Step 2/5] Turning Chaos ON");
            ChaosRule delayRule = ChaosRule.builder().faultType(ChaosRule.FaultType.DELAY).probability(0.3).delayMs(1200).targetTopic("all").build();
            ChaosRule duplicateRule = ChaosRule.builder().faultType(ChaosRule.FaultType.DUPLICATE).probability(0.2).targetTopic("all").build();
            ChaosRule mutateRule = ChaosRule.builder().faultType(ChaosRule.FaultType.MUTATE).probability(0.05).targetTopic("all").build();

            Flux.just(delayRule, duplicateRule, mutateRule)
                .flatMap(chaosSvcClient::createChaosRule)
                .doOnNext(activeRules::add)
                .blockLast(); // Wait for all rules to be created
            log.info("Chaos rules created: {}", activeRules);

            // Step 3: Measure (Simulated)
            log.info("[Gameday Step 3/5] Measuring system under chaos...");
            Thread.sleep(5000); // Simulate measurement period

            // Step 4: Auto-Heal (Simulated)
            log.info("[Gameday Step 4/5] Initiating auto-heal...");
            log.info("Scaling consumers +1 (simulated)");
            log.info("Retrying DLQ batch (simulated)");
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("GameDay scenario was interrupted", e);
        } finally {
            // Step 5: Chaos OFF
            log.info("[Gameday Step 5/5] Turning Chaos OFF");
            Flux.fromIterable(activeRules)
                .flatMap(rule -> chaosSvcClient.deleteChaosRule(rule.getId()))
                .blockLast();
            log.info("All chaos rules have been deleted.");
            log.info("--- GameDay Scenario Finished ---");
        }
    }
}
