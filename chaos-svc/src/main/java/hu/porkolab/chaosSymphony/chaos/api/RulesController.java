package hu.porkolab.chaosSymphony.chaos.api;

import hu.porkolab.chaosSymphony.chaos.core.RulesStore;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chaos")
public class RulesController {

	private static final Logger log = LoggerFactory.getLogger(RulesController.class);
	private final RulesStore store;

	public RulesController(RulesStore store) {
		this.store = store;
	}

	
	@GetMapping("/rules")
	public ResponseEntity<Map<String, ChaosRules.Rule>> getRules() {
		return ResponseEntity.ok(store.get());
	}

	
	@PostMapping("/rules")
	public ResponseEntity<Map<String, ChaosRules.Rule>> setRules(
			@RequestBody Map<String, Map<String, Object>> body) {

		Map<String, ChaosRules.Rule> converted = body.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> {
							Map<String, Object> v = e.getValue();
							double pDrop = ((Number) v.getOrDefault("pDrop", 0)).doubleValue();
							double pDup = ((Number) v.getOrDefault("pDup", 0)).doubleValue();
							int maxDelayMs = ((Number) v.getOrDefault("maxDelayMs", 0)).intValue();
							double pCorrupt = ((Number) v.getOrDefault("pCorrupt", 0)).doubleValue();
							return new ChaosRules.Rule(pDrop, pDup, maxDelayMs, pCorrupt);
						}));

		store.set(converted);
		log.info("Chaos rules updated: {}", converted.keySet());
		return ResponseEntity.ok(store.get());
	}

	
	@DeleteMapping("/rules")
	public ResponseEntity<Map<String, ChaosRules.Rule>> clearRules() {
		store.set(Map.of());
		log.info("Chaos rules cleared");
		return ResponseEntity.ok(store.get());
	}

	@GetMapping("/rules/{topic}")
	public ResponseEntity<ChaosRules.Rule> getRule(@PathVariable String topic) {
		ChaosRules.Rule rule = store.get().get(topic);
		if (rule == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(rule);
	}

	@PutMapping("/rules/{topic}")
	public ResponseEntity<ChaosRules.Rule> updateRule(
			@PathVariable String topic,
			@RequestBody Map<String, Object> body) {

		double pDrop = ((Number) body.getOrDefault("pDrop", 0)).doubleValue();
		double pDup = ((Number) body.getOrDefault("pDup", 0)).doubleValue();
		int maxDelayMs = ((Number) body.getOrDefault("maxDelayMs", 0)).intValue();
		double pCorrupt = ((Number) body.getOrDefault("pCorrupt", 0)).doubleValue();

		ChaosRules.Rule rule = new ChaosRules.Rule(pDrop, pDup, maxDelayMs, pCorrupt);
		var current = new java.util.HashMap<>(store.get()); 
		current.put(topic, rule);
		store.set(current);

		log.info("Chaos rule updated for {}", topic);
		return ResponseEntity.ok(rule);
	}

	@DeleteMapping("/rules/{topic}")
	public ResponseEntity<Map<String, ChaosRules.Rule>> deleteRule(@PathVariable String topic) {
		var current = new java.util.HashMap<>(store.get()); 
		if (current.containsKey(topic)) {
			current.remove(topic);
			store.set(current);
			log.info("Chaos rule deleted for {}", topic);
			return ResponseEntity.ok(current);
		}
		return ResponseEntity.notFound().build();
	}

    
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("service", "chaos-svc");
        resp.put("status", "UP");
        resp.put("port", 8085);
        resp.put("ruleCount", store.get().size());
        resp.put("topics", store.get().keySet());
        return resp;
    }
}
