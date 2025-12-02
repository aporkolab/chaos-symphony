package hu.porkolab.chaosSymphony.inventory.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentOutboxTest {

    private IdempotentOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new IdempotentOutbox();
    }

    @Test
    @DisplayName("Should return true for first occurrence")
    void shouldReturnTrueForFirstOccurrence() {
        boolean result = outbox.markIfFirst("key-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for duplicate")
    void shouldReturnFalseForDuplicate() {
        outbox.markIfFirst("key-1");

        boolean result = outbox.markIfFirst("key-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle multiple different keys")
    void shouldHandleMultipleDifferentKeys() {
        assertThat(outbox.markIfFirst("key-1")).isTrue();
        assertThat(outbox.markIfFirst("key-2")).isTrue();
        assertThat(outbox.markIfFirst("key-3")).isTrue();

        assertThat(outbox.markIfFirst("key-1")).isFalse();
        assertThat(outbox.markIfFirst("key-2")).isFalse();
    }

    @Test
    @DisplayName("Should distinguish similar keys")
    void shouldDistinguishSimilarKeys() {
        assertThat(outbox.markIfFirst("order:123")).isTrue();
        assertThat(outbox.markIfFirst("order:1234")).isTrue();
        assertThat(outbox.markIfFirst("order:123")).isFalse();
    }

    @Test
    @DisplayName("Should handle composite keys")
    void shouldHandleCompositeKeys() {
        assertThat(outbox.markIfFirst("order-123|abc123")).isTrue();
        assertThat(outbox.markIfFirst("order-123|abc124")).isTrue();
        assertThat(outbox.markIfFirst("order-123|abc123")).isFalse();
    }
}
