package hu.porkolab.chaosSymphony.orchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@ConfigurationProperties(prefix = "canary.payment")
@Slf4j
public class CanaryProperties {
    
    private final AtomicReference<Double> percentageRef = new AtomicReference<>(0.0);
    
    
    public void setPercentage(double percentage) {
        log.info("Setting canary percentage to {}", percentage);
        this.percentageRef.set(percentage);
    }
    
    public double getPercentage() {
        return percentageRef.get();
    }
    
    
    public double getPaymentPercentage() {
        return getPercentage();
    }
    
    public void setPaymentPercentage(double percentage) {
        setPercentage(percentage);
    }
}
