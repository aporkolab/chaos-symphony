package hu.porkolab.chaosSymphony.payment.config;

import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ChaosConfig {
	@Bean
	public ChaosRules chaosRules() {
		return new ChaosRules(Map.of(
				"topic:payment.result", new ChaosRules.Rule(0.10, 0.05, 300, 0.02),
				"type:InventoryRequested", new ChaosRules.Rule(0.00, 0.00, 100, 0.00)));
	}
}
