package hu.porkolab.chaosSymphony.gameday.service;

import hu.porkolab.chaosSymphony.gameday.model.ChaosRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ChaosSvcClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ChaosSvcClient chaosSvcClient;

    @BeforeEach
    void setUp() {
        chaosSvcClient = new ChaosSvcClient(webClient);
    }

    @Test
    void shouldCreateChaosRule() {
        ChaosRule rule = ChaosRule.builder()
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.3)
            .delayMs(1000)
            .targetTopic("all")
            .build();

        ChaosRule createdRule = ChaosRule.builder()
            .id("created-id")
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.3)
            .delayMs(1000)
            .targetTopic("all")
            .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ChaosRule.class)).thenReturn(Mono.just(createdRule));

        StepVerifier.create(chaosSvcClient.createChaosRule(rule))
            .expectNext(createdRule)
            .verifyComplete();
    }

    @Test
    void shouldDeleteChaosRule() {
        String ruleId = "rule-123";

        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        StepVerifier.create(chaosSvcClient.deleteChaosRule(ruleId))
            .verifyComplete();
    }

    @Test
    void shouldHandleCreateError() {
        ChaosRule rule = ChaosRule.builder()
            .faultType(ChaosRule.FaultType.DROP)
            .probability(0.1)
            .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ChaosRule.class))
            .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        StepVerifier.create(chaosSvcClient.createChaosRule(rule))
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void shouldHandleDeleteError() {
        String ruleId = "rule-456";

        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class))
            .thenReturn(Mono.error(new RuntimeException("Delete failed")));

        StepVerifier.create(chaosSvcClient.deleteChaosRule(ruleId))
            .expectError(RuntimeException.class)
            .verify();
    }
}
