package hu.porkolab.chaosSymphony.payment.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    @DisplayName("Should create PaymentStatus record")
    void shouldCreatePaymentStatusRecord() {
        PaymentStatus status = new PaymentStatus("order-123", "CHARGED");

        assertThat(status.orderId()).isEqualTo("order-123");
        assertThat(status.status()).isEqualTo("CHARGED");
    }

    @Test
    @DisplayName("Should have correct equals and hashCode")
    void shouldHaveCorrectEqualsAndHashCode() {
        PaymentStatus status1 = new PaymentStatus("order-123", "CHARGED");
        PaymentStatus status2 = new PaymentStatus("order-123", "CHARGED");
        PaymentStatus status3 = new PaymentStatus("order-456", "PENDING");

        assertThat(status1).isEqualTo(status2);
        assertThat(status1).isNotEqualTo(status3);
        assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
    }

    @Test
    @DisplayName("Should have correct toString")
    void shouldHaveCorrectToString() {
        PaymentStatus status = new PaymentStatus("order-123", "CHARGED");

        assertThat(status.toString()).contains("order-123", "CHARGED");
    }
}
