package hu.porkolab.chaosSymphony.orderapi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        // For development, we'll accept any username/password and return a dummy token.
        String username = loginRequest.get("username");

        return ResponseEntity.ok(Map.of(
            "token", "dummy-jwt-token-for-" + username,
            "id", 1,
            "username", username,
            "roles", List.of("ROLE_USER")
        ));
    }
}
