package hu.porkolab.chaosSymphony.streams.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    private MetricsConfig config;
    private MeterRegistry registry;

    @BeforeEach
    void setup() {
        config = new MetricsConfig();
        registry = new SimpleMeterRegistry();
    }

    @Test
    void shouldRegister1hGauge() {
        Gauge gauge = config.sloBurnRate1h(registry);

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getName()).isEqualTo(MetricsConfig.SLO_BURN_RATE_GAUGE);
        assertThat(gauge.getId().getTag("window")).isEqualTo("1h");
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldRegister6hGauge() {
        Gauge gauge = config.sloBurnRate6h(registry);

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getName()).isEqualTo(MetricsConfig.SLO_BURN_RATE_GAUGE);
        assertThat(gauge.getId().getTag("window")).isEqualTo("6h");
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldUpdate1hBurnRate() {
        Gauge gauge = config.sloBurnRate1h(registry);

        config.updateSloBurnRate1h(42);

        assertThat(gauge.value()).isEqualTo(42.0);
    }

    @Test
    void shouldUpdate6hBurnRate() {
        Gauge gauge = config.sloBurnRate6h(registry);

        config.updateSloBurnRate6h(99);

        assertThat(gauge.value()).isEqualTo(99.0);
    }

    @Test
    void shouldUpdateIndependently() {
        Gauge gauge1h = config.sloBurnRate1h(registry);
        Gauge gauge6h = config.sloBurnRate6h(registry);

        config.updateSloBurnRate1h(10);
        config.updateSloBurnRate6h(20);

        assertThat(gauge1h.value()).isEqualTo(10.0);
        assertThat(gauge6h.value()).isEqualTo(20.0);
    }

    @Test
    void shouldOverwritePreviousValue() {
        Gauge gauge = config.sloBurnRate1h(registry);

        config.updateSloBurnRate1h(50);
        config.updateSloBurnRate1h(75);

        assertThat(gauge.value()).isEqualTo(75.0);
    }
}
