package hu.porkolab.chaosSymphony.common.kafka;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class KafkaGracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(KafkaGracefulShutdown.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public KafkaGracefulShutdown(KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    
    @PreDestroy
    public void onShutdown() {
        log.info("Initiating graceful shutdown of Kafka consumers...");

        var containers = kafkaListenerEndpointRegistry.getAllListenerContainers();
        
        if (containers.isEmpty()) {
            log.info("No Kafka listener containers to stop");
            return;
        }

        log.info("Stopping {} Kafka listener container(s)", containers.size());

        for (MessageListenerContainer container : containers) {
            String listenerId = container.getListenerId();
            
            if (container.isRunning()) {
                log.info("Stopping Kafka listener: {}", listenerId);
                container.stop(() -> log.info("Kafka listener {} stopped successfully", listenerId));
            } else {
                log.debug("Kafka listener {} already stopped", listenerId);
            }
        }

        
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(SHUTDOWN_TIMEOUT_SECONDS);
        
        for (MessageListenerContainer container : containers) {
            long remainingTime = deadline - System.currentTimeMillis();
            
            if (remainingTime <= 0) {
                log.warn("Shutdown timeout exceeded, forcing stop");
                break;
            }

            if (container.isRunning()) {
                try {
                    log.debug("Waiting for container {} to stop ({}ms remaining)", 
                            container.getListenerId(), remainingTime);
                    Thread.sleep(Math.min(100, remainingTime));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Shutdown interrupted");
                    break;
                }
            }
        }

        
        long runningCount = containers.stream().filter(MessageListenerContainer::isRunning).count();
        
        if (runningCount > 0) {
            log.warn("Graceful shutdown completed with {} container(s) still running", runningCount);
        } else {
            log.info("Graceful shutdown completed - all Kafka consumers stopped");
        }
    }
}
