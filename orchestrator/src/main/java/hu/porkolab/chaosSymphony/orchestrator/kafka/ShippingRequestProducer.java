package hu.porkolab.chaosSymphony.orchestrator.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShippingRequestProducer {

	private static final Logger log = LoggerFactory.getLogger(ShippingRequestProducer.class);
	private final KafkaTemplate<String, String> kafka;

	public ShippingRequestProducer(KafkaTemplate<String, String> kafka) {
		this.kafka = kafka;
	}

	public void sendRequest(String orderId, String payloadJson) {
		try {
			String msg = EnvelopeHelper.envelope(orderId, "ShippingRequested", payloadJson);
			RecordMetadata md = kafka.send("shipping.requested", orderId, msg).get().getRecordMetadata();
			log.info("[ORCH] → shipping.requested key={} {}-{}@{}", orderId, md.topic(), md.partition(), md.offset());
		} catch (Exception e) {
			log.error("[ORCH] send shipping.requested failed key={} err={}", orderId, e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}
}
