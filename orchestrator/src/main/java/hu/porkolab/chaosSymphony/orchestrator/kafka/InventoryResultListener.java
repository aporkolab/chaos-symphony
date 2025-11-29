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
	public void onResult(ConsumerRecord<String, String> rec) {
		if (!idempotencyStore.markIfFirst(rec.key())) {
			log.warn("Duplicate message detected, skipping: {}", rec.key());
			return;
		}

		EventEnvelope env;
		JsonNode msg;
		try {
			env = EnvelopeHelper.parse(rec.value());
			msg = om.readTree(env.getPayload());
		} catch (Exception e) {
			log.error("Failed to parse inventory.result message: {}", e.getMessage());
			return;
		}

		String orderId = env.getOrderId();
		String status = msg.path("status").asText("");
		String reservationId = msg.path("reservationId").asText(null);

		if (orderId == null || orderId.isBlank()) {
			log.error("Missing orderId in inventory.result, skipping");
			return;
		}

		log.info("Orchestrator got InventoryResult: orderId={}, status={}", orderId, status);

		switch (status) {
			case "RESERVED" -> {
				
				sagaOrchestrator.onInventoryReserved(orderId, reservationId);

				
				String address = sagaOrchestrator.getShippingAddress(orderId);
				if (address == null || address.isBlank()) {
					log.warn("No shipping address found for orderId={}, using default", orderId);
					address = "Default Address - Please Update";
				}

				ObjectNode payload = om.createObjectNode()
						.put("orderId", orderId)
						.put("address", address);
				sagaOrchestrator.onShippingRequested(orderId);

				shippingProducer.sendRequest(orderId, payload.toString());
				log.debug("Shipping request sent for orderId={} to address={}", orderId, address);
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
