package hu.porkolab.chaosSymphony.payment.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public static final String PAYMENTS_PROCESSED_COUNTER = "payments.processed";
    public static final String DLT_MESSAGES_TOTAL_COUNTER = "dlt.messages.total";
    public static final String PROCESSING_TIME_TIMER = "processing.time.ms";

    @Bean
    public Counter paymentsProcessedMain(MeterRegistry registry) {
        return Counter.builder(PAYMENTS_PROCESSED_COUNTER)
                .tag("channel", "main")
                .description("The number of payment requests processed by the main consumer.")
                .register(registry);
    }

    @Bean
    public Counter paymentsProcessedCanary(MeterRegistry registry) {
        return Counter.builder(PAYMENTS_PROCESSED_COUNTER)
                .tag("channel", "canary")
                .description("The number of payment requests processed by the canary consumer.")
                .register(registry);
    }

    @Bean
    public Counter dltMessagesTotal(MeterRegistry registry) {
        return Counter.builder(DLT_MESSAGES_TOTAL_COUNTER)
                .description("Total number of messages sent to the Dead-Letter Topic.")
                .register(registry);
    }

    @Bean
    public Timer processingTime(MeterRegistry registry) {
        return Timer.builder(PROCESSING_TIME_TIMER)
                .description("Measures the end-to-end processing time of a message.")
                .publishPercentileHistogram()
                .register(registry);
    }
}
