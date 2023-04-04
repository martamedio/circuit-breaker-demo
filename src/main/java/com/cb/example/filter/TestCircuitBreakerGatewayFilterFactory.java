package com.cb.example.filter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.DispatcherHandler;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only difference with OSS version is support of shortcut configuration and extra last params
 * - e.g. CircuitBreaker=cbName,forward:/fallback-url/endpoint
 * - e.g. CircuitBreaker=cbName,forward:/fallback-url/endpoint,33,2m
 */
@Component
public class TestCircuitBreakerGatewayFilterFactory
		extends AbstractGatewayFilterFactory<TestCircuitBreakerGatewayFilterFactory.Resilience4JExtendedConfig> {

	private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;

	public TestCircuitBreakerGatewayFilterFactory(
			ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		this.reactiveCircuitBreakerFactory = reactiveCircuitBreakerFactory;
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("name",
				"fallbackUri",
				"statusCodes",
				"failureRateThreshold",
				"waitIntervalInOpenState");
	}

	public Customizer<ReactiveResilience4JCircuitBreakerFactory> customizer(
			Resilience4JExtendedConfig config) {
		return factory -> {
			factory.configure(builder -> {
						CircuitBreakerConfig.Builder custom = CircuitBreakerConfig.custom();
						if (config.getFailureRateThreshold() != null) {
							custom.failureRateThreshold(config.getFailureRateThreshold());
						}
						if (config.getWaitIntervalInOpenState() != null) {
							custom.waitIntervalFunctionInOpenState(
									IntervalFunction.of(config.getWaitIntervalInOpenState()));
						}
						builder.circuitBreakerConfig(custom.build());
					},
					config.getId());
		};
	}

	@Override
	public GatewayFilter apply(Resilience4JExtendedConfig config) {
		if (config.hasResilience4JCustomizations()) {
			customizer(config)
					.customize((ReactiveResilience4JCircuitBreakerFactory) reactiveCircuitBreakerFactory);
		}

		var filterFactory = new SpringCloudCircuitBreakerResilience4JFilterFactory(reactiveCircuitBreakerFactory,
				dispatcherHandlerProvider);

		return filterFactory.apply(config);
	}

	private void updateStatusCodes(Resilience4JExtendedConfig config) {
		if (hasShortcutStatusCodeConfiguration(config)) {
			config.setStatusCodes(splitShortcutStatusCode(config));
		}
	}

	private Set<String> splitShortcutStatusCode(Resilience4JExtendedConfig config) {
		return config.getStatusCodes().stream()
				.flatMap(s -> Arrays.stream(s.split(":")))
				.collect(Collectors.toSet());
	}

	private boolean hasShortcutStatusCodeConfiguration(Resilience4JExtendedConfig config) {
		return config.getStatusCodes().stream().anyMatch(s -> s.contains(":"));
	}

	@Override
	public Class<Resilience4JExtendedConfig> getConfigClass() {
		return Resilience4JExtendedConfig.class;
	}

	static class Resilience4JExtendedConfig extends SpringCloudCircuitBreakerFilterFactory.Config {

		private Float failureRateThreshold;
		private Duration waitIntervalInOpenState;

		public Float getFailureRateThreshold() {
			return failureRateThreshold;
		}

		public void setFailureRateThreshold(Float failureRateThreshold) {
			this.failureRateThreshold = failureRateThreshold;
		}

		public boolean hasResilience4JCustomizations() {
			return failureRateThreshold != null || waitIntervalInOpenState != null;
		}

		public Duration getWaitIntervalInOpenState() {
			return waitIntervalInOpenState;
		}

		public void setWaitIntervalInOpenState(Duration waitIntervalInOpenState) {
			this.waitIntervalInOpenState = waitIntervalInOpenState;
		}
	}
}
