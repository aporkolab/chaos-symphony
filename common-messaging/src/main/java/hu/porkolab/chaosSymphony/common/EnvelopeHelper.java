package hu.porkolab.chaosSymphony.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public final class EnvelopeHelper {
  private static final ObjectMapper OM = new ObjectMapper();

  private EnvelopeHelper() {
  }

  /** Eddigi 3-paraméteres (backward-compatible). eventId-t generál. */
  public static String envelope(String orderId, String type, String payloadJson) {
    String eventId = UUID.randomUUID().toString();
    return envelope(orderId, eventId, type, payloadJson);
  }

  /** ÚJ: 4-paraméteres overload explicit eventId-vel. */
  public static String envelope(String orderId, String eventId, String type, String payloadJson) {
    try {
      // payloadJson validálása (opcionális, de jó sanity)
      if (payloadJson == null || payloadJson.isBlank()) {
        payloadJson = "{}";
      } else {
        // ellenőrizzük, hogy tényleg JSON
        OM.readTree(payloadJson);
      }

      var root = OM.createObjectNode();
      root.put("orderId", orderId);
      root.put("eventId", eventId);
      root.put("type", type);
      // payloadot raw stringként tesszük be
      root.put("payload", payloadJson);
      return OM.writeValueAsString(root);
    } catch (Exception e) {
      throw new RuntimeException("Envelope build failed", e);
    }
  }

  /** Bejövő JSON → EventEnvelope (payload raw string marad). */
  public static EventEnvelope parse(String json) {
    try {
      JsonNode n = OM.readTree(json);
      String orderId = getText(n, "orderId", null);
      String eventId = getText(n, "eventId", null);
      String type = getText(n, "type", null);
      String payload = getText(n, "payload", "{}"); // raw string
      return new EventEnvelope(orderId, eventId, type, payload);
    } catch (Exception e) {
      throw new RuntimeException("Envelope parse failed", e);
    }
  }

  private static String getText(JsonNode n, String field, String deflt) {
    JsonNode v = n.get(field);
    return (v == null || v.isNull()) ? deflt : v.asText(deflt);
  }
}
