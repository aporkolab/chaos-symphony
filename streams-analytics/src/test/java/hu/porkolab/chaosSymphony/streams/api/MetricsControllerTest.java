package hu.porkolab.chaosSymphony.streams.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock WebClient webClient;
    @Mock WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock WebClient.ResponseSpec responseSpec;

    private MetricsController controller;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        controller = new MetricsController(webClient);
    }

    @SuppressWarnings("unchecked")
    private void mockPrometheusResponse(String jsonResponse) throws Exception {
        JsonNode node = mapper.readTree(jsonResponse);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(node));
    }

    @Test
    void shouldReturnSloMetrics() throws Exception {
        String prometheusResponse = """
            {"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1234567890,"42.5"]}]}}
            """;

        mockPrometheusResponse(prometheusResponse);

        var result = controller.getSloMetrics().block();

        assertThat(result).isNotNull();
        assertThat(result.p95Latency()).isEqualTo(42.5);
        assertThat(result.dltCount()).isEqualTo(42L);
        assertThat(result.sloBurnRate1h()).isEqualTo(42.5);
    }

    @Test
    void shouldReturnZeroOnEmptyResult() throws Exception {
        String emptyResponse = """
            {"status":"success","data":{"resultType":"vector","result":[]}}
            """;

        mockPrometheusResponse(emptyResponse);

        var result = controller.getSloMetrics().block();

        assertThat(result).isNotNull();
        assertThat(result.p95Latency()).isEqualTo(0.0);
        assertThat(result.dltCount()).isEqualTo(0L);
        assertThat(result.sloBurnRate1h()).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnZeroOnError() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.error(new RuntimeException("Connection failed")));

        var result = controller.getSloMetrics().block();

        assertThat(result).isNotNull();
        assertThat(result.p95Latency()).isEqualTo(0.0);
        assertThat(result.dltCount()).isEqualTo(0L);
        assertThat(result.sloBurnRate1h()).isEqualTo(0.0);
    }

    @Test
    void shouldParseIntegerValue() throws Exception {
        String intResponse = """
            {"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1234567890,"100"]}]}}
            """;

        mockPrometheusResponse(intResponse);

        var result = controller.getSloMetrics().block();

        assertThat(result).isNotNull();
        assertThat(result.dltCount()).isEqualTo(100L);
    }

    @Test
    void shouldHandleMalformedResponse() throws Exception {
        String malformed = """
            {"status":"success","data":{"resultType":"vector","result":[{"metric":{}}]}}
            """;

        mockPrometheusResponse(malformed);

        var result = controller.getSloMetrics().block();

        assertThat(result).isNotNull();
        assertThat(result.p95Latency()).isEqualTo(0.0);
    }
}
