package hu.porkolab.chaosSymphony.gameday.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosRuleTest {

    @Test
    void shouldBuildChaosRuleWithAllFields() {
        ChaosRule rule = ChaosRule.builder()
            .id("rule-123")
            .targetTopic("payment.requested")
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.5)
            .delayMs(1000)
            .build();

        assertThat(rule.getId()).isEqualTo("rule-123");
        assertThat(rule.getTargetTopic()).isEqualTo("payment.requested");
        assertThat(rule.getFaultType()).isEqualTo(ChaosRule.FaultType.DELAY);
        assertThat(rule.getProbability()).isEqualTo(0.5);
        assertThat(rule.getDelayMs()).isEqualTo(1000);
    }

    @Test
    void shouldBuildChaosRuleWithoutOptionalFields() {
        ChaosRule rule = ChaosRule.builder()
            .faultType(ChaosRule.FaultType.DROP)
            .probability(0.1)
            .build();

        assertThat(rule.getId()).isNull();
        assertThat(rule.getDelayMs()).isNull();
        assertThat(rule.getFaultType()).isEqualTo(ChaosRule.FaultType.DROP);
    }

    @Test
    void shouldSupportAllFaultTypes() {
        assertThat(ChaosRule.FaultType.values()).containsExactly(
            ChaosRule.FaultType.DELAY,
            ChaosRule.FaultType.DUPLICATE,
            ChaosRule.FaultType.MUTATE,
            ChaosRule.FaultType.DROP
        );
    }

    @Test
    void shouldConvertFaultTypeFromString() {
        assertThat(ChaosRule.FaultType.valueOf("DELAY")).isEqualTo(ChaosRule.FaultType.DELAY);
        assertThat(ChaosRule.FaultType.valueOf("DUPLICATE")).isEqualTo(ChaosRule.FaultType.DUPLICATE);
        assertThat(ChaosRule.FaultType.valueOf("MUTATE")).isEqualTo(ChaosRule.FaultType.MUTATE);
        assertThat(ChaosRule.FaultType.valueOf("DROP")).isEqualTo(ChaosRule.FaultType.DROP);
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        ChaosRule rule1 = ChaosRule.builder()
            .id("rule-1")
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.3)
            .build();

        ChaosRule rule2 = ChaosRule.builder()
            .id("rule-1")
            .faultType(ChaosRule.FaultType.DELAY)
            .probability(0.3)
            .build();

        assertThat(rule1).isEqualTo(rule2);
        assertThat(rule1.hashCode()).isEqualTo(rule2.hashCode());
    }

    @Test
    void shouldSupportSetters() {
        ChaosRule rule = ChaosRule.builder().build();

        rule.setId("new-id");
        rule.setTargetTopic("new-topic");
        rule.setFaultType(ChaosRule.FaultType.MUTATE);
        rule.setProbability(0.75);
        rule.setDelayMs(500);

        assertThat(rule.getId()).isEqualTo("new-id");
        assertThat(rule.getTargetTopic()).isEqualTo("new-topic");
        assertThat(rule.getFaultType()).isEqualTo(ChaosRule.FaultType.MUTATE);
        assertThat(rule.getProbability()).isEqualTo(0.75);
        assertThat(rule.getDelayMs()).isEqualTo(500);
    }

    @Test
    void shouldSupportToString() {
        ChaosRule rule = ChaosRule.builder()
            .id("rule-123")
            .faultType(ChaosRule.FaultType.DELAY)
            .build();

        assertThat(rule.toString()).contains("ChaosRule");
        assertThat(rule.toString()).contains("rule-123");
        assertThat(rule.toString()).contains("DELAY");
    }
}
