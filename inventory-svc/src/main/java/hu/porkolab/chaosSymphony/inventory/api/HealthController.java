package hu.porkolab.chaosSymphony.inventory.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/healthz")
    public String ok() {
        return "OK - inventory-svc";
    }
}
