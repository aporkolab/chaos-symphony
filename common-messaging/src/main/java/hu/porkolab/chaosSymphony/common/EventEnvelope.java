package hu.porkolab.chaosSymphony.common;

public class EventEnvelope {
    private String orderId;
    private String eventId;
    private String type;
    private String payload; // raw JSON string

    public EventEnvelope() {
    }

    public EventEnvelope(String orderId, String eventId, String type, String payload) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.type = type;
        this.payload = payload;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
