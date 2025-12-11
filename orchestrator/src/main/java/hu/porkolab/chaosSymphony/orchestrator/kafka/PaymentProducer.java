package hu.porkolab.chaosSymphony.orchestrator.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Environment environment;

    public void sendPaymentRequested(String orderId, String paymentPayloadJson) {
        String msg = EnvelopeHelper.envelope(orderId, "PaymentRequested", paymentPayloadJson);

        double canaryPercentage = environment.getProperty("canary.payment.percentage", Double.class, 0.0);
        
        String topic = ThreadLocalRandom.current().nextDouble() < canaryPercentage 
                ? "payment.requested.canary" 
                : "payment.requested";
        
        kafkaTemplate.send(topic, orderId, msg)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICAL: Failed to send payment request for orderId={} to topic {}: {}", 
                                orderId, topic, ex.getMessage(), ex);
                    } else {
                        log.debug("Payment request sent for orderId={} to topic {}", orderId, topic);
                    }
                });
    }
}