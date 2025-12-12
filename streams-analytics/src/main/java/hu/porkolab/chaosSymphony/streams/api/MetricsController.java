package hu.porkolab.chaosSymphony.streams.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final WebClient prometheusWebClient;

    public record SloMetrics(double p95Latency, long dltCount, double sloBurnRate1h) {}

    @GetMapping(value = "/slo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SloMetrics> getSloMetrics() {
        
        
        Mono<Double> p95LatencyMono = queryPrometheus(
            "histogram_quantile(0.95, sum(rate(processing_time_ms_seconds_bucket[5m])) by (le)) * 1000"
        );
        
        Mono<Double> dltCountMono = queryPrometheus("sum(dlt_messages_total) or vector(0)");
        Mono<Double> sloBurnRateMono = queryPrometheus("orders_slo_burn_rate or vector(0)");

        return Mono.zip(p95LatencyMono, dltCountMono, sloBurnRateMono)
                .map(tuple -> new SloMetrics(
                        tuple.getT1(),
                        tuple.getT2().longValue(),
                        tuple.getT3()
                ));
    }

    private Mono<Double> queryPrometheus(String query) {
        return prometheusWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parsePrometheusValue)
                .onErrorReturn(0.0);
    }

    private Double parsePrometheusValue(JsonNode response) {
        try {
            JsonNode result = response.path("data").path("result");
            if (result.isArray() && result.size() > 0) {
                JsonNode value = result.get(0).path("value");
                if (value.isArray() && value.size() > 1) {
                    return value.get(1).asDouble();
                }
            }
        } catch (Exception e) {
            
        }
        return 0.0;
    }
}
