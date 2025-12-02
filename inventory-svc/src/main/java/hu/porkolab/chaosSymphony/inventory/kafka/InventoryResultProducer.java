package hu.porkolab.chaosSymphony.inventory.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.inventory.outbox.IdempotentOutbox;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryResultProducer {

	private static final Logger log = LoggerFactory.getLogger(InventoryResultProducer.class);

	private final KafkaTemplate<String, String> kafka;
	private final IdempotentOutbox outbox;

	public InventoryResultProducer(KafkaTemplate<String, String> kafka, IdempotentOutbox outbox) {
		this.kafka = kafka;
		this.outbox = outbox;
	}

	public void sendResult(String orderId, String payloadJson) {
		String eventId = UUID.randomUUID().toString();
		String outKey = orderId + "|" + Integer.toHexString(payloadJson.hashCode());

		if (!outbox.markIfFirst(outKey)) {
			log.debug("[INVENTORY] duplicate result suppressed key={}", outKey);
			return;
		}

		try {
			String msg = EnvelopeHelper.envelope(orderId, eventId, "InventoryResult", payloadJson);
			RecordMetadata md = kafka.send("inventory.result", orderId, msg).get().getRecordMetadata();
			log.info("[INVENTORY] → inventory.result key={} {}-{}@{}", orderId, md.topic(), md.partition(), md.offset());
		} catch (Exception e) {
			log.error("[INVENTORY] send inventory.result failed key={} err={}", orderId, e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}
}
