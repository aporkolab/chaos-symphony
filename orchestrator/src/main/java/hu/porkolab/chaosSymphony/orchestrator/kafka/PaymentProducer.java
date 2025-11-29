package hu.porkolab.chaosSymphony.orchestrator.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.common.chaos.ChaosProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProducer {
    private final ChaosProducer chaosProducer;
    private final Environment environment;

    public void sendPaymentRequested(String orderId, String paymentPayloadJson) {
        String msg = EnvelopeHelper.envelope(orderId, "PaymentRequested", paymentPayloadJson);

        double canaryPercentage = environment.getProperty("canary.payment.percentage", Double.class, 0.0);
        
        String topic = ThreadLocalRandom.current().nextDouble() < canaryPercentage 
                ? "payment.requested.canary" 
                : "payment.requested";
        
        try {
            chaosProducer.send(topic, orderId, "PaymentRequested", msg);
            log.debug("Payment request sent for orderId={} to topic {}", orderId, topic);
        } catch (ChaosProducer.ChaosDropException e) {
            log.warn("[CHAOS] Payment request DROPPED for orderId={} to topic {}", orderId, topic);
            throw e; 
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send payment request for orderId={} to topic {}: {}", 
                    orderId, topic, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}