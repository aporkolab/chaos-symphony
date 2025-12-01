package hu.porkolab.chaosSymphony.payment.config;

import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
public class ChaosRefreshConfig {

	private static final Logger log = LoggerFactory.getLogger(ChaosRefreshConfig.class);

	private final AtomicReference<ChaosRules> rulesRef = new AtomicReference<>(new ChaosRules(Map.of()));

	private final RestClient chaosRest;

	public ChaosRefreshConfig(RestClient.Builder builder) {
		String base = System.getProperty("chaos.url",
				System.getenv().getOrDefault("CHAOS_URL", "http://localhost:8085"));
		this.chaosRest = builder.baseUrl(base).build();
	}

	@Scheduled(fixedDelayString = "${chaos.refresh-ms:5000}")
	public void refresh() {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Map<String, Object>> m = chaosRest.get()
					.uri("/api/chaos/rules")
					.retrieve()
					.body(Map.class);
			if (m != null) {
				var typed = m.entrySet().stream().collect(Collectors.toMap(
						e -> "topic:" + e.getKey(),  
						e -> {
							Map<String, Object> v = e.getValue();
							double pDrop = ((Number) v.getOrDefault("pDrop", 0)).doubleValue();
							double pDup = ((Number) v.getOrDefault("pDup", 0)).doubleValue();
							int maxDelayMs = ((Number) v.getOrDefault("maxDelayMs", 0)).intValue();
							double pCorrupt = ((Number) v.getOrDefault("pCorrupt", 0)).doubleValue();
							return new ChaosRules.Rule(pDrop, pDup, maxDelayMs, pCorrupt);
						}));
				rulesRef.set(new ChaosRules(typed));
				log.info("[CHAOS] rules refreshed: {} keys", typed.size());
			}
		} catch (Exception e) {
			log.debug("[CHAOS] refresh failed: {}", e.getMessage());
		}
	}

	@Bean
	public Supplier<ChaosRules> chaosRulesSupplier() {
		return rulesRef::get;
	}

	@Bean
	public ChaosProducer chaosProducer(KafkaTemplate<String, String> tpl,
			Supplier<ChaosRules> supplier) {
		return new ChaosProducer(tpl, supplier);
	}
}
