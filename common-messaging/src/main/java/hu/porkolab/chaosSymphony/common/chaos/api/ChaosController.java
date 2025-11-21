package hu.porkolab.chaosSymphony.common.chaos.api;

import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final AtomicReference<Map<String, ChaosRules.Rule>> rulesRef =
            new AtomicReference<>(Map.of());

    @GetMapping("/rules")
    public Map<String, ChaosRules.Rule> get() {
        return rulesRef.get();
    }

    @PostMapping("/rules")
    public Map<String, ChaosRules.Rule> set(@RequestBody Map<String, ChaosRules.Rule> body) {
        rulesRef.set(body);
        return body;
    }

    public ChaosRules rules() {
        return new ChaosRules(rulesRef.get());
    }
}
