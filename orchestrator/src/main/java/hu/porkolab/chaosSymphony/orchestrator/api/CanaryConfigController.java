package hu.porkolab.chaosSymphony.orchestrator.api;

import hu.porkolab.chaosSymphony.orchestrator.config.CanaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/canary")
@RequiredArgsConstructor
@Slf4j
public class CanaryConfigController {

    private final CanaryProperties canaryProperties;

    public record CanaryConfig(boolean enabled, double percentage) {}

    @GetMapping("/config")
    public CanaryConfig getConfig() {
        double percentage = canaryProperties.getPaymentPercentage();
        return new CanaryConfig(percentage > 0, percentage);
    }

    @PostMapping("/config")
    public CanaryConfig setConfig(@RequestBody CanaryConfig config) {
        double percentageToSet = config.enabled() ? config.percentage() : 0.0;
        canaryProperties.setPaymentPercentage(percentageToSet);
        log.info("Canary config updated: enabled={}, percentage={}", config.enabled(), percentageToSet);
        return new CanaryConfig(config.enabled(), percentageToSet);
    }
}
