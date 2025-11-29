package hu.porkolab.chaosSymphony.orchestrator.config;

import hu.porkolab.chaosSymphony.common.idemp.IdempotencyStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestConfig {

    @Bean
    public IdempotencyStore idempotencyStore() {
        // Provide a simple in-memory implementation for testing
        return new IdempotencyStore() {
            private final Set<String> processedKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

            @Override
            public boolean markIfFirst(String key) {
                return processedKeys.add(key);
            }
        };
    }
}
