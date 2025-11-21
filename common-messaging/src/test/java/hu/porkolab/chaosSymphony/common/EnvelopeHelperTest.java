package hu.porkolab.chaosSymphony.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class EnvelopeHelperTest {

	@Test
	@DisplayName("Should create and parse envelope with 3 params (auto eventId)")
	void roundtrip_with3Params_shouldWork() throws Exception {
		String msg = EnvelopeHelper.envelope("OID", "PaymentRequested", "{\"x\":1}");
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getOrderId()).isEqualTo("OID");
		assertThat(env.getType()).isEqualTo("PaymentRequested");
		assertThat(env.getPayload()).isEqualTo("{\"x\":1}");
		assertThat(env.getEventId()).isNotNull().isNotEmpty(); 
	}

	@Test
	@DisplayName("Should create and parse envelope with 4 params (explicit eventId)")
	void roundtrip_with4Params_shouldPreserveEventId() throws Exception {
		String msg = EnvelopeHelper.envelope("order-123", "event-456", "OrderCreated", "{\"amount\":100}");
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getOrderId()).isEqualTo("order-123");
		assertThat(env.getEventId()).isEqualTo("event-456");
		assertThat(env.getType()).isEqualTo("OrderCreated");
		assertThat(env.getPayload()).isEqualTo("{\"amount\":100}");
	}

	@Test
	@DisplayName("Should handle null payload by defaulting to empty object")
	void envelope_withNullPayload_shouldDefaultToEmptyObject() throws Exception {
		String msg = EnvelopeHelper.envelope("order-1", "event-1", "TestType", null);
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getPayload()).isEqualTo("{}");
	}

	@Test
	@DisplayName("Should handle blank payload by defaulting to empty object")
	void envelope_withBlankPayload_shouldDefaultToEmptyObject() throws Exception {
		String msg = EnvelopeHelper.envelope("order-1", "event-1", "TestType", "   ");
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getPayload()).isEqualTo("{}");
	}

	@Test
	@DisplayName("Should throw on invalid JSON payload")
	void envelope_withInvalidJson_shouldThrow() {
		assertThatThrownBy(() -> 
			EnvelopeHelper.envelope("order-1", "event-1", "TestType", "not-valid-json")
		).isInstanceOf(RuntimeException.class)
		 .hasMessageContaining("Envelope build failed");
	}

	@Test
	@DisplayName("Should throw on invalid JSON when parsing")
	void parse_withInvalidJson_shouldThrow() {
		assertThatThrownBy(() -> 
			EnvelopeHelper.parse("this is not json")
		).isInstanceOf(RuntimeException.class)
		 .hasMessageContaining("Envelope parse failed");
	}

	@Test
	@DisplayName("Should handle missing fields with defaults")
	void parse_withMissingFields_shouldUseDefaults() throws Exception {
		String json = "{\"orderId\":\"order-only\"}";
		EventEnvelope env = EnvelopeHelper.parse(json);
		
		assertThat(env.getOrderId()).isEqualTo("order-only");
		assertThat(env.getEventId()).isNull();
		assertThat(env.getType()).isNull();
		assertThat(env.getPayload()).isEqualTo("{}");
	}

	@Test
	@DisplayName("Should handle complex nested payload")
	void roundtrip_withComplexPayload_shouldPreserve() throws Exception {
		String complexPayload = "{\"user\":{\"name\":\"John\",\"age\":30},\"items\":[1,2,3]}";
		String msg = EnvelopeHelper.envelope("order-x", "event-y", "ComplexEvent", complexPayload);
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getPayload()).isEqualTo(complexPayload);
	}

	@Test
	@DisplayName("Should generate unique eventIds for 3-param calls")
	void envelope_calledMultipleTimes_shouldGenerateUniqueEventIds() throws Exception {
		String msg1 = EnvelopeHelper.envelope("order", "Type", "{}");
		String msg2 = EnvelopeHelper.envelope("order", "Type", "{}");
		
		EventEnvelope env1 = EnvelopeHelper.parse(msg1);
		EventEnvelope env2 = EnvelopeHelper.parse(msg2);
		
		assertThat(env1.getEventId()).isNotEqualTo(env2.getEventId());
	}

	@Test
	@DisplayName("Should handle null orderId")
	void envelope_withNullOrderId_shouldAcceptNull() throws Exception {
		String msg = EnvelopeHelper.envelope(null, "event-1", "Type", "{}");
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getOrderId()).isNull();
	}

	@Test
	@DisplayName("Should handle special characters in payload")
	void roundtrip_withSpecialCharacters_shouldPreserve() throws Exception {
		String payload = "{\"message\":\"Hello\\nWorld\\t!\",\"emoji\":\"🎉\"}";
		String msg = EnvelopeHelper.envelope("order", "event", "Type", payload);
		EventEnvelope env = EnvelopeHelper.parse(msg);
		
		assertThat(env.getPayload()).isEqualTo(payload);
	}
}
