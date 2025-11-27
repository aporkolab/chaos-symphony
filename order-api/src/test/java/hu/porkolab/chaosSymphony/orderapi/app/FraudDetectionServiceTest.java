package hu.porkolab.chaosSymphony.orderapi.app;

import hu.porkolab.chaosSymphony.orderapi.app.FraudDetectionService.FraudAction;
import hu.porkolab.chaosSymphony.orderapi.app.FraudDetectionService.FraudCheckResult;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FraudDetectionService covering all fraud detection rules:
 * - High-value order detection
 * - Velocity checks (multiple orders in short time)
 * - Round number detection
 * - Score thresholds for review/reject decisions
 */
@DisplayName("FraudDetectionService")
class FraudDetectionServiceTest {

    private FraudDetectionService fraudService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fraudService = new FraudDetectionService(meterRegistry);
        ReflectionTestUtils.setField(fraudService, "velocityWindowMinutes", 60);
        ReflectionTestUtils.setField(fraudService, "velocityMaxOrders", 5);
    }

    @Nested
    @DisplayName("High Value Order Detection")
    class HighValueOrderTests {

        @Test
        @DisplayName("Order above $1000 should be flagged for review")
        void highValueOrder_shouldFlagForReview() {
            Order order = createOrder("1500.00", "cust-high-value");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.requiresReview()).isTrue();
            assertThat(result.action()).isEqualTo(FraudAction.REVIEW);
            assertThat(result.score()).isGreaterThanOrEqualTo(new BigDecimal("20.00"));
            assertThat(result.reason()).contains("High value order");
        }

        @Test
        @DisplayName("Order above $2000 should have higher risk score")
        void veryHighValueOrder_shouldHaveHigherScore() {
            Order order = createOrder("2500.00", "cust-very-high");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.requiresReview()).isTrue();
            assertThat(result.score()).isGreaterThanOrEqualTo(new BigDecimal("35.00"));
        }

        @Test
        @DisplayName("Order above $5000 should have maximum value risk score")
        void extremelyHighValueOrder_shouldHaveMaxValueScore() {
            Order order = createOrder("6000.00", "cust-extreme");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.requiresReview()).isTrue();
            assertThat(result.score()).isGreaterThanOrEqualTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Order below $1000 should not trigger high-value flag")
        void lowValueOrder_shouldNotFlagForHighValue() {
            Order order = createOrder("500.00", "cust-low-value");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.reason()).doesNotContain("High value order");
        }
    }

    @Nested
    @DisplayName("Velocity Check Detection")
    class VelocityCheckTests {

        @Test
        @DisplayName("Multiple orders from same customer should trigger velocity flag")
        void velocityCheck_shouldFlagAfterMultipleOrders() {
            String customerId = "velocity-test-" + UUID.randomUUID();

            // Place multiple orders to trigger velocity check
            for (int i = 0; i < 5; i++) {
                Order order = createOrder("50.00", customerId);
                fraudService.evaluate(order);
            }

            // The next order should have velocity score
            Order finalOrder = createOrder("50.00", customerId);
            FraudCheckResult result = fraudService.evaluate(finalOrder);

            assertThat(result.score()).isGreaterThan(BigDecimal.ZERO);
            assertThat(result.reason()).contains("Velocity anomaly");
        }

        @Test
        @DisplayName("First order from customer should not trigger velocity flag")
        void firstOrder_shouldNotTriggerVelocity() {
            String customerId = "new-customer-" + UUID.randomUUID();
            Order order = createOrder("100.00", customerId);

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.reason()).doesNotContain("Velocity");
        }

        @Test
        @DisplayName("Different customers should have independent velocity tracking")
        void differentCustomers_shouldHaveIndependentVelocity() {
            String customer1 = "customer-1-" + UUID.randomUUID();
            String customer2 = "customer-2-" + UUID.randomUUID();

            // Place multiple orders for customer1
            for (int i = 0; i < 5; i++) {
                fraudService.evaluate(createOrder("50.00", customer1));
            }

            // Customer2's first order should not be affected
            Order customer2Order = createOrder("50.00", customer2);
            FraudCheckResult result = fraudService.evaluate(customer2Order);

            assertThat(result.reason()).doesNotContain("Velocity");
        }
    }

    @Nested
    @DisplayName("Round Number Detection")
    class RoundNumberTests {

        @Test
        @DisplayName("Round $100 amount should add suspicious score")
        void roundHundred_shouldAddScore() {
            Order order = createOrder("100.00", "cust-round-100");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.reason()).contains("round amount");
            assertThat(result.score()).isGreaterThanOrEqualTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("Round $50 amount should add suspicious score")
        void roundFifty_shouldAddScore() {
            Order order = createOrder("150.00", "cust-round-50");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.reason()).contains("round amount");
        }

        @Test
        @DisplayName("Non-round amount should not trigger round number flag")
        void nonRoundAmount_shouldNotFlag() {
            Order order = createOrder("123.45", "cust-not-round");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.reason()).doesNotContain("round");
        }
    }

    @Nested
    @DisplayName("Score Thresholds")
    class ScoreThresholdTests {

        @Test
        @DisplayName("Score below 70 should approve order")
        void lowScore_shouldApprove() {
            Order order = createOrder("50.00", "cust-low-risk-" + UUID.randomUUID());

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.action()).isEqualTo(FraudAction.APPROVE);
            assertThat(result.isRejected()).isFalse();
            assertThat(result.requiresReview()).isFalse();
        }

        @Test
        @DisplayName("High-value orders should always require review regardless of score")
        void highValue_shouldAlwaysRequireReview() {
            // Even with low fraud indicators, high value alone triggers review
            Order order = createOrder("1001.00", "trusted-customer-" + UUID.randomUUID());

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.requiresReview()).isTrue();
        }

        @Test
        @DisplayName("Combined risk factors should accumulate score")
        void combinedRiskFactors_shouldAccumulateScore() {
            String customerId = "risky-customer-" + UUID.randomUUID();

            // Build up velocity
            for (int i = 0; i < 5; i++) {
                fraudService.evaluate(createOrder("50.00", customerId));
            }

            // High value + round number + velocity
            Order riskyOrder = createOrder("5000.00", customerId);
            FraudCheckResult result = fraudService.evaluate(riskyOrder);

            // Should have high combined score (50 value + 40 velocity + 15 round = 105, capped at 100)
            assertThat(result.score()).isGreaterThanOrEqualTo(new BigDecimal("90.00"));
            // Score >= 90 triggers REJECT, not REVIEW
            assertThat(result.isRejected()).isTrue();
        }

        @Test
        @DisplayName("Score should be capped at 100")
        void score_shouldBeCappedAt100() {
            String customerId = "max-risk-" + UUID.randomUUID();

            // Build up maximum velocity
            for (int i = 0; i < 10; i++) {
                fraudService.evaluate(createOrder("100.00", customerId));
            }

            // Extreme high value + round number + velocity
            Order extremeOrder = createOrder("10000.00", customerId);
            FraudCheckResult result = fraudService.evaluate(extremeOrder);

            assertThat(result.score()).isLessThanOrEqualTo(new BigDecimal("100.00"));
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("Should increment orders scanned counter")
        void shouldIncrementOrdersScannedCounter() {
            double before = meterRegistry.counter("fraud.orders.scanned").count();

            fraudService.evaluate(createOrder("100.00", "metrics-test"));

            double after = meterRegistry.counter("fraud.orders.scanned").count();
            assertThat(after).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("Should increment orders flagged counter for high-value orders")
        void shouldIncrementOrdersFlaggedCounter() {
            double before = meterRegistry.counter("fraud.orders.flagged").count();

            fraudService.evaluate(createOrder("1500.00", "flagged-test-" + UUID.randomUUID()));

            double after = meterRegistry.counter("fraud.orders.flagged").count();
            assertThat(after).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null customerId gracefully")
        void nullCustomerId_shouldNotThrow() {
            Order order = Order.builder()
                .id(UUID.randomUUID())
                .total(new BigDecimal("100.00"))
                .customerId(null)
                .build();

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result).isNotNull();
            assertThat(result.action()).isNotNull();
        }

        @Test
        @DisplayName("Should handle zero amount")
        void zeroAmount_shouldNotFlag() {
            Order order = createOrder("0.00", "zero-amount-customer");

            FraudCheckResult result = fraudService.evaluate(order);

            assertThat(result.action()).isEqualTo(FraudAction.APPROVE);
        }

        @Test
        @DisplayName("Should handle exact threshold amount $1000")
        void exactThreshold_shouldNotFlag() {
            Order order = createOrder("1000.00", "exact-threshold-" + UUID.randomUUID());

            FraudCheckResult result = fraudService.evaluate(order);

            // $1000 exactly is NOT above threshold, but is a round number
            assertThat(result.reason()).doesNotContain("High value order");
            assertThat(result.reason()).contains("round amount");
        }
    }

    // Helper method to create test orders
    private Order createOrder(String amount, String customerId) {
        return Order.builder()
            .id(UUID.randomUUID())
            .total(new BigDecimal(amount))
            .customerId(customerId)
            .build();
    }
}
