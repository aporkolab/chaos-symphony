package hu.porkolab.chaosSymphony.payment.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusStoreTest {

    private PaymentStatusStore store;

    @BeforeEach
    void setUp() {
        store = new PaymentStatusStore();
    }

    @Test
    @DisplayName("Should save and retrieve status")
    void shouldSaveAndRetrieveStatus() {
        store.save("order-1", "CHARGED");

        Optional<String> result = store.getStatus("order-1");

        assertThat(result).isPresent().contains("CHARGED");
    }

    @Test
    @DisplayName("Should return empty for unknown order")
    void shouldReturnEmptyForUnknownOrder() {
        Optional<String> result = store.getStatus("unknown-order");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should update existing status")
    void shouldUpdateExistingStatus() {
        store.save("order-1", "PENDING");
        store.save("order-1", "CHARGED");

        Optional<String> result = store.getStatus("order-1");

        assertThat(result).isPresent().contains("CHARGED");
    }

    @Test
    @DisplayName("Should handle multiple orders")
    void shouldHandleMultipleOrders() {
        store.save("order-1", "CHARGED");
        store.save("order-2", "PENDING");
        store.save("order-3", "REFUNDED");

        assertThat(store.getStatus("order-1")).contains("CHARGED");
        assertThat(store.getStatus("order-2")).contains("PENDING");
        assertThat(store.getStatus("order-3")).contains("REFUNDED");
    }

    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAllEntries() {
        store.save("order-1", "CHARGED");
        store.save("order-2", "PENDING");

        store.clear();

        assertThat(store.getStatus("order-1")).isEmpty();
        assertThat(store.getStatus("order-2")).isEmpty();
    }
}
