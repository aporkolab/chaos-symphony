package hu.porkolab.chaosSymphony.orderapi.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {
	private final KafkaTemplate<String, String> kafka;

	public void sendOutbox(String orderId, String outboxJson) {
		kafka.send("ordersdb.public.order_outbox", orderId, outboxJson)
			.whenComplete((result, ex) -> {
				if (ex != null) {
					log.error("CRITICAL: Failed to send outbox event for orderId [{}]: {}", 
						orderId, ex.getMessage(), ex);
				} else {
					log.debug("Outbox event sent for orderId [{}]", orderId);
				}
			});
	}
}
