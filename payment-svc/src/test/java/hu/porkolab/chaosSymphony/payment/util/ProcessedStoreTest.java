package hu.porkolab.chaosSymphony.payment.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedStoreTest {

    private ProcessedStore store;

    @BeforeEach
    void setUp() {
        store = new ProcessedStore();
    }

    @Test
    @DisplayName("Should return false for first occurrence (not seen)")
    void shouldReturnFalseForFirstOccurrence() {
        boolean result = store.seen("id-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return true for duplicate (already seen)")
    void shouldReturnTrueForDuplicate() {
        store.seen("id-1");

        boolean result = store.seen("id-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should track multiple IDs independently")
    void shouldTrackMultipleIdsIndependently() {
        assertThat(store.seen("id-1")).isFalse();
        assertThat(store.seen("id-2")).isFalse();
        assertThat(store.seen("id-3")).isFalse();

        assertThat(store.seen("id-1")).isTrue();
        assertThat(store.seen("id-2")).isTrue();
        
        assertThat(store.seen("id-4")).isFalse();
    }

    @Test
    @DisplayName("Should handle empty string ID")
    void shouldHandleEmptyStringId() {
        assertThat(store.seen("")).isFalse();
        assertThat(store.seen("")).isTrue();
    }
}
