package hu.porkolab.chaosSymphony.payment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRequestProducer {

	private static final String TOPIC = "inventory.requested";
	private final KafkaTemplate<String, String> kafkaTemplate;

	public void sendRequest(String key, String payload) {
		log.debug("Sending inventory request for key [{}]: {}", key, payload);
		kafkaTemplate.send(TOPIC, key, payload)
			.whenComplete((result, ex) -> {
				if (ex != null) {
					log.error("CRITICAL: Failed to send inventory request for key [{}]: {}", 
						key, ex.getMessage(), ex);
				} else {
					log.debug("Inventory request sent successfully for key [{}]", key);
				}
			});
	}
}