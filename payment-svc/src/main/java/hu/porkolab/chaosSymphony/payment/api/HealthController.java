package hu.porkolab.chaosSymphony.payment.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/healthz")
    public String ok() {
        return "OK - payment-svc";
    }

    // This endpoint is created specifically to satisfy the Pact contract test
    // @GetMapping("/api/payments/status/{orderId}")
    // public Map<String, String> getPaymentStatus(@PathVariable String orderId) {
    //     // In a real application, this would look up the status from a database or service.
    //     // For the contract test, we just need to return a response with the correct structure.
    //     return Map.of("orderId", orderId, "status", "CHARGED");
    // }
}
