package hu.porkolab.chaosSymphony.payment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationProducer {

	private static final String TOPIC = "order.compensation";
	private final KafkaTemplate<String, String> kafkaTemplate;

	public void sendCompensation(String key, String payload) {
		log.warn("Sending order compensation for key [{}]: {}", key, payload);
		kafkaTemplate.send(TOPIC, key, payload)
			.whenComplete((result, ex) -> {
				if (ex != null) {
					log.error("CRITICAL: Failed to send order compensation for key [{}]: {}", 
						key, ex.getMessage(), ex);
				}
			});
	}
}