package hu.porkolab.chaosSymphony.common.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;


import java.util.function.Supplier;

public class ChaosProducer {
    private static final Logger log = LoggerFactory.getLogger(ChaosProducer.class);

    private final KafkaTemplate<String,String> kafka;
    private final Supplier<ChaosRules> rulesSupplier;

    public ChaosProducer(KafkaTemplate<String,String> kafka, Supplier<ChaosRules> rulesSupplier) {
        this.kafka = kafka;
        this.rulesSupplier = rulesSupplier;
    }


    public void send(String topic, String key, String type, String msg) {
        ChaosRules.Rule rule = rulesSupplier.get().ruleFor(topic, type);
        ChaosRules rules = rulesSupplier.get();
        rules.maybeDelay(rule.maxDelayMs());
        if (rules.hit(rule.pDrop())) { 
            log.warn("[CHAOS] DROP topic={} key={} type={}", topic, key, type); 
            throw new ChaosDropException("Chaos DROP triggered for topic=" + topic + " key=" + key);
        }
        if (rules.hit(rule.pCorrupt())) {
            int cut = Math.max(1, msg.length()/2);
            msg = msg.substring(0, cut);
            log.warn("[CHAOS] CORRUPT topic={} key={} type={} cut={}", topic, key, type, cut);
        }
        kafka.send(topic, key, msg);
        if (rules.hit(rule.pDup())) {
            kafka.send(topic, key, msg);
            log.warn("[CHAOS] DUP topic={} key={} type={}", topic, key, type);
        }
    }
    
    
    public static class ChaosDropException extends RuntimeException {
        public ChaosDropException(String message) {
            super(message);
        }
    }
}
