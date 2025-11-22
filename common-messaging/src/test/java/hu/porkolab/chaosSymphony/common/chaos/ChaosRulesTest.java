package hu.porkolab.chaosSymphony.common.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


class ChaosRulesTest {

    @Nested
    @DisplayName("Rule Resolution Tests")
    class RuleResolutionTests {

        @Test
        @DisplayName("Should return topic-specific rule when available")
        void ruleFor_withTopicRule_shouldReturnTopicRule() {
            
            ChaosRules.Rule topicRule = new ChaosRules.Rule(0.5, 0.1, 1000, 0.05);
            ChaosRules.Rule typeRule = new ChaosRules.Rule(0.1, 0.01, 100, 0.01);
            
            ChaosRules rules = new ChaosRules(Map.of(
                    "topic:payment.requested", topicRule,
                    "type:PaymentRequested", typeRule
            ));

            
            ChaosRules.Rule result = rules.ruleFor("payment.requested", "PaymentRequested");

            
            assertThat(result).isEqualTo(topicRule);
        }

        @Test
        @DisplayName("Should fall back to type-specific rule when no topic rule exists")
        void ruleFor_withoutTopicRule_shouldReturnTypeRule() {
            
            ChaosRules.Rule typeRule = new ChaosRules.Rule(0.2, 0.02, 200, 0.02);
            
            ChaosRules rules = new ChaosRules(Map.of(
                    "type:PaymentRequested", typeRule
            ));

            
            ChaosRules.Rule result = rules.ruleFor("payment.requested", "PaymentRequested");

            
            assertThat(result).isEqualTo(typeRule);
        }

        @Test
        @DisplayName("Should return default rule when no matching rules exist")
        void ruleFor_withNoMatchingRules_shouldReturnDefaultRule() {
            
            ChaosRules rules = new ChaosRules(Map.of(
                    "topic:other.topic", new ChaosRules.Rule(0.5, 0.1, 1000, 0.05)
            ));

            
            ChaosRules.Rule result = rules.ruleFor("payment.requested", "PaymentRequested");

            
            assertThat(result.pDrop()).isZero();
            assertThat(result.pDup()).isZero();
            assertThat(result.maxDelayMs()).isZero();
            assertThat(result.pCorrupt()).isZero();
        }

        @Test
        @DisplayName("Should return default rule for empty rules map")
        void ruleFor_withEmptyMap_shouldReturnDefaultRule() {
            
            ChaosRules rules = new ChaosRules(Map.of());

            
            ChaosRules.Rule result = rules.ruleFor("any.topic", "AnyType");

            
            assertThat(result.pDrop()).isZero();
            assertThat(result.pDup()).isZero();
            assertThat(result.maxDelayMs()).isZero();
            assertThat(result.pCorrupt()).isZero();
        }
    }

    @Nested
    @DisplayName("Delay Tests")
    class DelayTests {

        @Test
        @DisplayName("Should not delay when maxMs is zero")
        void maybeDelay_withZeroMaxMs_shouldNotDelay() {
            
            ChaosRules rules = new ChaosRules(Map.of());
            long startTime = System.currentTimeMillis();

            
            rules.maybeDelay(0);

            
            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isLessThan(50); 
        }

        @Test
        @DisplayName("Should not delay when maxMs is negative")
        void maybeDelay_withNegativeMaxMs_shouldNotDelay() {
            
            ChaosRules rules = new ChaosRules(Map.of());
            long startTime = System.currentTimeMillis();

            
            rules.maybeDelay(-100);

            
            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isLessThan(50);
        }

        @Test
        @DisplayName("Should delay within specified range")
        void maybeDelay_withPositiveMaxMs_shouldDelayWithinRange() {
            
            ChaosRules rules = new ChaosRules(Map.of());
            int maxDelay = 100;
            long startTime = System.currentTimeMillis();

            
            rules.maybeDelay(maxDelay);

            
            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isLessThanOrEqualTo(maxDelay + 50); 
        }
    }

    @Nested
    @DisplayName("Probability Hit Tests")
    class ProbabilityHitTests {

        @Test
        @DisplayName("Should always return true for probability 1.0")
        void hit_withProbabilityOne_shouldAlwaysReturnTrue() {
            
            ChaosRules rules = new ChaosRules(Map.of());

            
            for (int i = 0; i < 100; i++) {
                assertThat(rules.hit(1.0)).isTrue();
            }
        }

        @Test
        @DisplayName("Should always return false for probability 0.0")
        void hit_withProbabilityZero_shouldAlwaysReturnFalse() {
            
            ChaosRules rules = new ChaosRules(Map.of());

            
            for (int i = 0; i < 100; i++) {
                assertThat(rules.hit(0.0)).isFalse();
            }
        }

        @RepeatedTest(5)
        @DisplayName("Should hit approximately with given probability over many trials")
        void hit_withHalfProbability_shouldHitApproximatelyHalfTheTime() {
            
            ChaosRules rules = new ChaosRules(Map.of());
            double probability = 0.5;
            int trials = 10000;
            AtomicInteger hits = new AtomicInteger(0);

            
            for (int i = 0; i < trials; i++) {
                if (rules.hit(probability)) {
                    hits.incrementAndGet();
                }
            }

            
            double actualRatio = (double) hits.get() / trials;
            assertThat(actualRatio).isCloseTo(probability, within(0.1));
        }

        @Test
        @DisplayName("Should handle edge probability values")
        void hit_withEdgeProbabilities_shouldHandleCorrectly() {
            
            ChaosRules rules = new ChaosRules(Map.of());

            
            
            int verySmallHits = 0;
            for (int i = 0; i < 10000; i++) {
                if (rules.hit(0.001)) {
                    verySmallHits++;
                }
            }
            assertThat(verySmallHits).isGreaterThan(0).isLessThan(100);

            
            int veryLargeHits = 0;
            for (int i = 0; i < 100; i++) {
                if (rules.hit(0.999)) {
                    veryLargeHits++;
                }
            }
            assertThat(veryLargeHits).isGreaterThanOrEqualTo(90);
        }
    }

    @Nested
    @DisplayName("Rule Record Tests")
    class RuleRecordTests {

        @Test
        @DisplayName("Rule record should correctly store and return values")
        void ruleRecord_shouldStoreAndReturnValues() {
            
            double pDrop = 0.1;
            double pDup = 0.05;
            int maxDelayMs = 500;
            double pCorrupt = 0.02;

            
            ChaosRules.Rule rule = new ChaosRules.Rule(pDrop, pDup, maxDelayMs, pCorrupt);

            
            assertThat(rule.pDrop()).isEqualTo(pDrop);
            assertThat(rule.pDup()).isEqualTo(pDup);
            assertThat(rule.maxDelayMs()).isEqualTo(maxDelayMs);
            assertThat(rule.pCorrupt()).isEqualTo(pCorrupt);
        }

        @Test
        @DisplayName("Rule records with same values should be equal")
        void ruleRecords_withSameValues_shouldBeEqual() {
            
            ChaosRules.Rule rule1 = new ChaosRules.Rule(0.1, 0.05, 500, 0.02);
            ChaosRules.Rule rule2 = new ChaosRules.Rule(0.1, 0.05, 500, 0.02);

            
            assertThat(rule1).isEqualTo(rule2);
            assertThat(rule1.hashCode()).isEqualTo(rule2.hashCode());
        }
    }
}
