package hu.porkolab.chaosSymphony.payment.api;

import hu.porkolab.chaosSymphony.payment.store.PaymentStatusStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentStatusStore paymentStatusStore;

    @GetMapping("/status/{orderId}")
    public ResponseEntity<PaymentStatus> getPaymentStatus(@PathVariable String orderId) {
        return paymentStatusStore.getStatus(orderId)
                .map(status -> new PaymentStatus(orderId, status))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
