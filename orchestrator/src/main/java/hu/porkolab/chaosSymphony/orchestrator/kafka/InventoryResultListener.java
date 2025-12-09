package hu.porkolab.chaosSymphony.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import hu.porkolab.chaosSymphony.orchestrator.saga.SagaOrchestrator;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryResultListener {

	private final IdempotencyStore idempotencyStore;
	private final ShippingRequestProducer shippingProducer;
	private final SagaOrchestrator sagaOrchestrator;
	private final ObjectMapper om;
	private final Counter ordersFailed;

	@KafkaListener(topics = "inventory.result", groupId = "orchestrator-inventory-result")
	@Transactional
	public void onResult(ConsumerRecord<String, String> rec) throws Exception {
		if (!idempotencyStore.markIfFirst(rec.key())) {
			log.warn("Duplicate message detected, skipping: {}", rec.key());
			return;
		}

		EventEnvelope env = EnvelopeHelper.parse(rec.value());
		String orderId = env.getOrderId();
		JsonNode msg = om.readTree(env.getPayload());
		String status = msg.path("status").asText("");
		String reservationId = msg.path("reservationId").asText(null);

		log.info("Orchestrator got InventoryResult: orderId={}, status={}", orderId, status);

		switch (status) {
			case "RESERVED" -> {
				
				sagaOrchestrator.onInventoryReserved(orderId, reservationId);

				ObjectNode payload = om.createObjectNode()
						.put("orderId", orderId)
						.put("address", "Budapest");
				shippingProducer.sendRequest(orderId, payload.toString());
				log.debug("Shipping request sent for orderId={}", orderId);
			}
			case "OUT_OF_STOCK" -> {
				log.warn("Inventory OUT_OF_STOCK for orderId={}", orderId);
				sagaOrchestrator.onInventoryFailed(orderId, "Inventory out of stock");
				ordersFailed.increment();
			}
			default -> {
				log.warn("Unknown inventory status='{}' for orderId={}", status, orderId);
				sagaOrchestrator.onInventoryFailed(orderId, "Unknown inventory status: " + status);
				ordersFailed.increment();
			}
		}
	}
}
