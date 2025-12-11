package hu.porkolab.chaosSymphony.orchestrator.kafka;

import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class PaymentProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Environment environment;

    public void sendPaymentRequested(String orderId, String paymentPayloadJson) {
        String msg = EnvelopeHelper.envelope(orderId, "PaymentRequested", paymentPayloadJson);

        double canaryPercentage = environment.getProperty("canary.payment.percentage", Double.class, 0.0);
        
        if (ThreadLocalRandom.current().nextDouble() < canaryPercentage) {
            kafkaTemplate.send("payment.requested.canary", orderId, msg);
        } else {
            kafkaTemplate.send("payment.requested", orderId, msg);
        }
    }
}