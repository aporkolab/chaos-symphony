package hu.porkolab.chaosSymphony.streams.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    public static final String SLO_BURN_RATE_GAUGE = "orders.slo.burn.rate";

    // This AtomicLong will be updated by the service that queries the KStreams state store
    private final AtomicLong sloBurnRate1h = new AtomicLong(0);
    private final AtomicLong sloBurnRate6h = new AtomicLong(0);

    @Bean
    public Gauge sloBurnRate1h(MeterRegistry registry) {
        return Gauge.builder(SLO_BURN_RATE_GAUGE, sloBurnRate1h, AtomicLong::get)
                .tag("window", "1h")
                .description("SLO Burn Rate over the last 1 hour.")
                .register(registry);
    }

    @Bean
    public Gauge sloBurnRate6h(MeterRegistry registry) {
        return Gauge.builder(SLO_BURN_RATE_GAUGE, sloBurnRate6h, AtomicLong::get)
                .tag("window", "6h")
                .description("SLO Burn Rate over the last 6 hours.")
                .register(registry);
    }

    // Method to be called by the service to update the gauge
    public void updateSloBurnRate1h(long value) {
        this.sloBurnRate1h.set(value);
    }

    public void updateSloBurnRate6h(long value) {
        this.sloBurnRate6h.set(value);
    }
}
