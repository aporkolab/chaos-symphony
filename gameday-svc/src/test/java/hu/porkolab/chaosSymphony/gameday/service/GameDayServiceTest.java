package hu.porkolab.chaosSymphony.gameday.service;

import hu.porkolab.chaosSymphony.gameday.model.ChaosRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class GameDayServiceTest {

    @Mock
    private ChaosSvcClient chaosSvcClient;

    private GameDayService gameDayService;

    @BeforeEach
    void setUp() {
        gameDayService = new GameDayService(chaosSvcClient);
    }

    @Test
    @DisplayName("Should create chaos rules during game day scenario")
    void shouldCreateChaosRulesAndCleanup() {

        ChaosRule mockRule = ChaosRule.builder()
            .id("test-rule-id")
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.3)
            .build();

        when(chaosSvcClient.createChaosRule(any(ChaosRule.class)))
            .thenReturn(Mono.just(mockRule));
        when(chaosSvcClient.deleteChaosRule(anyString()))
            .thenReturn(Mono.empty());


        gameDayService.runGameDay();


        verify(chaosSvcClient, times(3)).createChaosRule(any(ChaosRule.class));


        verify(chaosSvcClient, atLeastOnce()).deleteChaosRule(anyString());
    }

    @Test
    @DisplayName("Should create correct chaos rule types")
    void shouldCreateCorrectChaosRuleTypes() {

        when(chaosSvcClient.createChaosRule(any(ChaosRule.class)))
            .thenAnswer(invocation -> {
                ChaosRule rule = invocation.getArgument(0);
                return Mono.just(ChaosRule.builder()
                    .id("id-" + rule.getFaultType())
                    .faultType(rule.getFaultType())
                    .probability(rule.getProbability())
                    .delayMs(rule.getDelayMs())
                    .targetTopic(rule.getTargetTopic())
                    .build());
            });
        when(chaosSvcClient.deleteChaosRule(anyString()))
            .thenReturn(Mono.empty());


        gameDayService.runGameDay();


        ArgumentCaptor<ChaosRule> ruleCaptor = ArgumentCaptor.forClass(ChaosRule.class);
        verify(chaosSvcClient, times(3)).createChaosRule(ruleCaptor.capture());

        List<ChaosRule> capturedRules = ruleCaptor.getAllValues();
        assertThat(capturedRules)
            .extracting(ChaosRule::getFaultType)
            .containsExactlyInAnyOrder(
                ChaosRule.FaultType.DELAY,
                ChaosRule.FaultType.DUPLICATE,
                ChaosRule.FaultType.MUTATE
            );


        ChaosRule delayRule = capturedRules.stream()
            .filter(r -> r.getFaultType() == ChaosRule.FaultType.DELAY)
            .findFirst()
            .orElseThrow();
        assertThat(delayRule.getProbability()).isEqualTo(0.3);
        assertThat(delayRule.getDelayMs()).isEqualTo(1200);
        assertThat(delayRule.getTargetTopic()).isEqualTo("all");
    }

    @Test
    @DisplayName("Should cleanup rules even when chaos client fails")
    void shouldCleanupRulesOnFailure() {

        ChaosRule mockRule = ChaosRule.builder()
            .id("test-rule-id")
            .faultType(ChaosRule.FaultType.DELAY)
            .build();

        when(chaosSvcClient.createChaosRule(any(ChaosRule.class)))
            .thenReturn(Mono.just(mockRule))
            .thenReturn(Mono.just(mockRule))
            .thenReturn(Mono.error(new RuntimeException("Chaos service unavailable")));
        when(chaosSvcClient.deleteChaosRule(anyString()))
            .thenReturn(Mono.empty());


        try {
            gameDayService.runGameDay();
        } catch (Exception e) {

        }


        verify(chaosSvcClient, atLeastOnce()).deleteChaosRule(anyString());
    }

    @Test
    @DisplayName("Should target all topics with chaos rules")
    void shouldTargetAllTopics() {

        when(chaosSvcClient.createChaosRule(any(ChaosRule.class)))
            .thenAnswer(invocation -> {
                ChaosRule rule = invocation.getArgument(0);
                return Mono.just(ChaosRule.builder()
                    .id("id-" + rule.getFaultType())
                    .faultType(rule.getFaultType())
                    .probability(rule.getProbability())
                    .delayMs(rule.getDelayMs())
                    .targetTopic(rule.getTargetTopic())
                    .build());
            });
        when(chaosSvcClient.deleteChaosRule(anyString()))
            .thenReturn(Mono.empty());


        gameDayService.runGameDay();


        ArgumentCaptor<ChaosRule> ruleCaptor = ArgumentCaptor.forClass(ChaosRule.class);
        verify(chaosSvcClient, times(3)).createChaosRule(ruleCaptor.capture());

        assertThat(ruleCaptor.getAllValues())
            .extracting(ChaosRule::getTargetTopic)
            .containsOnly("all");
    }
}
