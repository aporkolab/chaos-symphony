package hu.porkolab.chaosSymphony.orderapi.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EventEnvelope;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderOutbox;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventProducer {

	private final OrderOutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@SneakyThrows
	public void fire(Object event) {
		String payloadJson = objectMapper.writeValueAsString(event);
		String eventType = event.getClass().getSimpleName();

		
		UUID orderId = (UUID) event.getClass().getMethod("getOrderId").invoke(event);

		EventEnvelope envelope = new EventEnvelope(
				UUID.randomUUID().toString(),
				orderId.toString(),
				eventType,
				payloadJson);

		String envelopeJson = objectMapper.writeValueAsString(envelope);

		OrderOutbox outboxEvent = OrderOutbox.builder()
				.id(UUID.randomUUID())
				.aggregateId(orderId)
				.aggregateType("Order")
				.type(eventType)
				.payload(envelopeJson)
				.occurredAt(Instant.now())
				.build();

		outboxRepository.save(outboxEvent);
	}
}