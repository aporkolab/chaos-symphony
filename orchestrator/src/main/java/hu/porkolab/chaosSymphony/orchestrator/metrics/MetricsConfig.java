package hu.porkolab.chaosSymphony.orchestrator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

	private final AtomicLong sloBurnRate1h = new AtomicLong(0);
	
	
	private Counter ordersStartedCounter;
	private Counter ordersFailedCounter;

	@Bean
	public Counter ordersStarted(MeterRegistry registry) {
		this.ordersStartedCounter = Counter.builder("orders.started")
				.description("Number of orders started")
				.register(registry);
		return ordersStartedCounter;
	}

	@Bean
	public Counter ordersSucceeded(MeterRegistry registry) {
		return Counter.builder("orders.succeeded")
				.description("Number of orders succeeded")
				.register(registry);
	}

	@Bean
	public Counter ordersFailed(MeterRegistry registry) {
		this.ordersFailedCounter = Counter.builder("orders.failed")
				.description("Number of orders failed")
				.register(registry);
		return ordersFailedCounter;
	}

	@Bean
	public Counter compensationsTriggered(MeterRegistry registry) {
		return Counter.builder("saga.compensations.triggered")
				.description("Number of saga compensations triggered")
				.register(registry);
	}

	@Bean
	public Counter compensationsCompleted(MeterRegistry registry) {
		return Counter.builder("saga.compensations.completed")
				.description("Number of saga compensations completed")
				.register(registry);
	}

	@Bean
	public Timer processingTimeTimer(MeterRegistry registry) {
		return Timer.builder("processing_time_ms")
				.description("Order processing time")
				.publishPercentileHistogram()
				.register(registry);
	}

	@Bean
	public Counter dltMessagesTotal(MeterRegistry registry) {
		return Counter.builder("dlt_messages_total")
				.description("Total messages sent to DLT")
				.register(registry);
	}

	@Bean
	public Gauge sloBurnRateGauge(MeterRegistry registry) {
		return Gauge.builder("orders_slo_burn_rate", sloBurnRate1h, AtomicLong::doubleValue)
				.tag("window", "1h")
				.description("SLO burn rate over 1 hour window")
				.register(registry);
	}

	@Bean
	public AtomicLong sloBurnRateHolder() {
		return sloBurnRate1h;
	}
	
	
	@Scheduled(fixedRate = 10000)
	public void calculateBurnRate() {
		if (ordersStartedCounter == null || ordersFailedCounter == null) {
			return;
		}
		
		double started = ordersStartedCounter.count();
		double failed = ordersFailedCounter.count();
		
		if (started > 0) {
			double errorRate = (failed / started) * 100;
			sloBurnRate1h.set((long) errorRate);
		}
	}
}
