package hu.porkolab.chaosSymphony.dlq.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void ok_returnsExpectedMessage() {
        var hc = new HealthController();
        assertThat(hc.ok()).isEqualTo("OK - dlq-admin");
    }
}
