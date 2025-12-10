package hu.porkolab.chaosSymphony.orderapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private final AuthController controller = new AuthController();

    @Test
    void shouldAuthenticateUser() {
        Map<String, String> loginRequest = Map.of("username", "testuser");

        var response = controller.authenticateUser(loginRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token")).isEqualTo("dummy-jwt-token-for-testuser");
        assertThat(body.get("username")).isEqualTo("testuser");
        assertThat(body.get("id")).isEqualTo(1);
        assertThat(body.get("roles")).isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void shouldAuthenticateWithDifferentUsername() {
        Map<String, String> loginRequest = Map.of("username", "admin");

        var response = controller.authenticateUser(loginRequest);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token")).isEqualTo("dummy-jwt-token-for-admin");
        assertThat(body.get("username")).isEqualTo("admin");
    }
}
