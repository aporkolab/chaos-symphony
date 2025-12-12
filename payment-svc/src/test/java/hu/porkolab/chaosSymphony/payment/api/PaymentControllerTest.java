package hu.porkolab.chaosSymphony.payment.api;

import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentStatusStore paymentStatusStore;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentStatusStore);
    }

    @Test
    @DisplayName("Should return payment status when found")
    void shouldReturnPaymentStatusWhenFound() {
        String orderId = "order-123";
        when(paymentStatusStore.getStatus(orderId)).thenReturn(Optional.of("CHARGED"));

        ResponseEntity<PaymentStatus> response = controller.getPaymentStatus(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(orderId);
        assertThat(response.getBody().status()).isEqualTo("CHARGED");
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() {
        String orderId = "unknown-order";
        when(paymentStatusStore.getStatus(orderId)).thenReturn(Optional.empty());

        ResponseEntity<PaymentStatus> response = controller.getPaymentStatus(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Should return different statuses correctly")
    void shouldReturnDifferentStatusesCorrectly() {
        when(paymentStatusStore.getStatus("order-1")).thenReturn(Optional.of("PENDING"));
        when(paymentStatusStore.getStatus("order-2")).thenReturn(Optional.of("REFUNDED"));

        ResponseEntity<PaymentStatus> pending = controller.getPaymentStatus("order-1");
        ResponseEntity<PaymentStatus> refunded = controller.getPaymentStatus("order-2");

        assertThat(pending.getBody().status()).isEqualTo("PENDING");
        assertThat(refunded.getBody().status()).isEqualTo("REFUNDED");
    }
}
