package hu.porkolab.chaosSymphony.common.idemp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class JdbcIdempotencyStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcIdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        idempotencyStore = new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Test
    @DisplayName("Should return true for first occurrence of event ID")
    void markIfFirst_withNewEventId_shouldReturnTrue() {
        
        String eventId = "event-123";
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenReturn(1);

        
        boolean result = idempotencyStore.markIfFirst(eventId);

        
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for duplicate event ID")
    void markIfFirst_withDuplicateEventId_shouldReturnFalse() {
        
        String eventId = "event-123";
        when(jdbcTemplate.update(anyString(), eq(eventId)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        
        boolean result = idempotencyStore.markIfFirst(eventId);

        
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when zero rows affected")
    void markIfFirst_withZeroRowsAffected_shouldReturnFalse() {
        
        String eventId = "event-123";
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenReturn(0);

        
        boolean result = idempotencyStore.markIfFirst(eventId);

        
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should propagate unexpected exceptions")
    void markIfFirst_withUnexpectedError_shouldPropagateException() {
        
        String eventId = "event-123";
        RuntimeException unexpectedError = new RuntimeException("Database connection failed");
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenThrow(unexpectedError);

        
        assertThatThrownBy(() -> idempotencyStore.markIfFirst(eventId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection failed");
    }

    @Test
    @DisplayName("Should handle empty event ID")
    void markIfFirst_withEmptyEventId_shouldStillAttemptInsert() {
        
        String eventId = "";
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenReturn(1);

        
        boolean result = idempotencyStore.markIfFirst(eventId);

        
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should handle null event ID gracefully")
    void markIfFirst_withNullEventId_shouldPropagateException() {
        
        when(jdbcTemplate.update(anyString(), eq((String) null)))
                .thenThrow(new DataIntegrityViolationException("NULL not allowed"));

        
        boolean result = idempotencyStore.markIfFirst(null);

        
        assertThat(result).isFalse();
    }
}
