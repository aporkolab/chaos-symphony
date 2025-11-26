package hu.porkolab.chaosSymphony.orderapi.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestProducer {

	private final KafkaTemplate<String, String> kafka;

	public PaymentRequestProducer(KafkaTemplate<String, String> kafka) {
		this.kafka = kafka;
	}

	public void sendRequest(String orderId, String payloadJson) {
		try {
			String msg = EnvelopeHelper.envelope(orderId, "PaymentRequested", payloadJson);
			// Blokkoló küldés: ha baj van, itt kivételt kapunk
			RecordMetadata md = kafka.send("payment.requested", orderId, msg).get().getRecordMetadata();
			System.out.println("[ORDER-API] SENT payment.requested key=" + orderId +
					" -> " + md.topic() + "-" + md.partition() + "@" + md.offset());
		} catch (Exception e) {
			System.err.println("[ORDER-API] SEND FAIL payment.requested key=" + orderId + " err=" + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
