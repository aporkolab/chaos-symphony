package hu.porkolab.chaosSymphony.common.idemp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markIfFirst(String eventId) {
        try {
            
            
            int rows = jdbc.update("INSERT INTO idempotency_event(event_id) VALUES (?)", eventId);
            if (rows == 1) {
                log.debug("Event {} marked as processed", eventId);
                return true;
            }
            return false;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            
            log.debug("Duplicate event detected: {} (idempotency working correctly)", eventId);
            return false;
        } catch (Exception e) {
            
            log.error("Unexpected error checking idempotency for event {}: {}", eventId, e.getMessage(), e);
            throw e;
        }
    }
}