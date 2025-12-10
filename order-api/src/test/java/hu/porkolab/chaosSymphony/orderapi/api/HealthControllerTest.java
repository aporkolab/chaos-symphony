package hu.porkolab.chaosSymphony.orderapi.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void shouldReturnOk() {
        HealthController controller = new HealthController();
        assertThat(controller.ok()).isEqualTo("OK - order-api");
    }
}
