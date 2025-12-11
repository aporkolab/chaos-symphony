package hu.porkolab.chaosSymphony.common.idemp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopIdempotencyStore implements IdempotencyStore {
    
    private static final Logger log = LoggerFactory.getLogger(NoopIdempotencyStore.class);
    
    public NoopIdempotencyStore() {
        log.warn("Using NoopIdempotencyStore - duplicate event detection DISABLED");
    }
    
    @Override
    public boolean markIfFirst(String eventId) {
        return true;
    }
}
