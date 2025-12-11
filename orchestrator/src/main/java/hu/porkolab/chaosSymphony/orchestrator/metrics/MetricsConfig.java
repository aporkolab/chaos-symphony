package hu.porkolab.chaosSymphony.orchestrator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

	@Bean
	public Counter ordersStarted(MeterRegistry registry) {
		return Counter.builder("orders.started")
				.description("Number of orders started")
				.register(registry);
	}

	@Bean
	public Counter ordersSucceeded(MeterRegistry registry) {
		return Counter.builder("orders.succeeded")
				.description("Number of orders succeeded")
				.register(registry);
	}

	@Bean
	public Counter ordersFailed(MeterRegistry registry) {
		return Counter.builder("orders.failed")
				.description("Number of orders failed")
				.register(registry);
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
}
