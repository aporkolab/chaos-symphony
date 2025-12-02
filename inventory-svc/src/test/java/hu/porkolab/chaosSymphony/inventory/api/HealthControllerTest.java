package hu.porkolab.chaosSymphony.inventory.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    @DisplayName("Should return OK health status")
    void shouldReturnOkHealthStatus() {
        String result = controller.ok();

        assertThat(result).isEqualTo("OK - inventory-svc");
    }
}
