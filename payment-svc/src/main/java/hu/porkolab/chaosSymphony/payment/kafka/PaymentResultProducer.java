package hu.porkolab.chaosSymphony.payment.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import hu.porkolab.chaosSymphony.payment.outbox.IdempotentOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
public class PaymentResultProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultProducer.class);

    private final ChaosProducer chaosProducer;
    private final IdempotentOutbox outbox;

    public PaymentResultProducer(ChaosProducer chaosProducer, IdempotentOutbox outbox) {
        this.chaosProducer = chaosProducer;
        this.outbox = outbox;
    }

    public void sendResult(String orderId, String resultPayloadJson) {
        String eventId = UUID.randomUUID().toString();
        String outKey = orderId + "|" + Integer.toHexString(resultPayloadJson.hashCode());

        if (!outbox.markIfFirst(outKey)) {
            log.debug("[PAYMENT] duplicate result suppressed key={}", outKey);
            return;
        }

        try {
            String msg = EnvelopeHelper.envelope(orderId, eventId, "PaymentResult", resultPayloadJson);
            chaosProducer.send("payment.result", orderId, "PaymentResult", msg);
            log.info("[PAYMENT] → payment.result key={}", orderId);
        } catch (ChaosProducer.ChaosDropException e) {
            log.warn("[PAYMENT] Chaos DROP for payment.result key={}", orderId);
            throw e; 
        } catch (Exception e) {
            log.error("[PAYMENT] send payment.result failed key={} err={}", orderId, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
