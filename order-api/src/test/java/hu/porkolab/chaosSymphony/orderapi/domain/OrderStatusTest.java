package hu.porkolab.chaosSymphony.orderapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus")
class OrderStatusTest {

    @Test
    @DisplayName("Should have all expected values in correct order")
    void shouldHaveAllExpectedValues() {
        assertThat(OrderStatus.values()).containsExactly(
            OrderStatus.NEW,
            OrderStatus.PENDING_REVIEW,
            OrderStatus.APPROVED,
            OrderStatus.REJECTED,
            OrderStatus.PAID,
            OrderStatus.ALLOCATED,
            OrderStatus.SHIPPED,
            OrderStatus.FAILED
        );
    }

    @Nested
    @DisplayName("Terminal state checks")
    class TerminalStateChecks {

        @Test
        @DisplayName("SHIPPED should be terminal")
        void shippedShouldBeTerminal() {
            assertThat(OrderStatus.SHIPPED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("FAILED should be terminal")
        void failedShouldBeTerminal() {
            assertThat(OrderStatus.FAILED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("REJECTED should be terminal")
        void rejectedShouldBeTerminal() {
            assertThat(OrderStatus.REJECTED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("NEW should not be terminal")
        void newShouldNotBeTerminal() {
            assertThat(OrderStatus.NEW.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("PENDING_REVIEW should not be terminal")
        void pendingReviewShouldNotBeTerminal() {
            assertThat(OrderStatus.PENDING_REVIEW.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("Review requirement checks")
    class ReviewRequirementChecks {

        @Test
        @DisplayName("PENDING_REVIEW should require review")
        void pendingReviewShouldRequireReview() {
            assertThat(OrderStatus.PENDING_REVIEW.requiresReview()).isTrue();
        }

        @Test
        @DisplayName("Other states should not require review")
        void otherStatesShouldNotRequireReview() {
            assertThat(OrderStatus.NEW.requiresReview()).isFalse();
            assertThat(OrderStatus.APPROVED.requiresReview()).isFalse();
            assertThat(OrderStatus.PAID.requiresReview()).isFalse();
        }
    }

    @Nested
    @DisplayName("Payment eligibility checks")
    class PaymentEligibilityChecks {

        @Test
        @DisplayName("NEW orders can proceed to payment")
        void newCanProceedToPayment() {
            assertThat(OrderStatus.NEW.canProceedToPayment()).isTrue();
        }

        @Test
        @DisplayName("APPROVED orders can proceed to payment")
        void approvedCanProceedToPayment() {
            assertThat(OrderStatus.APPROVED.canProceedToPayment()).isTrue();
        }

        @Test
        @DisplayName("PENDING_REVIEW orders cannot proceed to payment")
        void pendingReviewCannotProceedToPayment() {
            assertThat(OrderStatus.PENDING_REVIEW.canProceedToPayment()).isFalse();
        }

        @Test
        @DisplayName("REJECTED orders cannot proceed to payment")
        void rejectedCannotProceedToPayment() {
            assertThat(OrderStatus.REJECTED.canProceedToPayment()).isFalse();
        }
    }

    @Test
    @DisplayName("Should convert from string correctly")
    void shouldConvertFromString() {
        assertThat(OrderStatus.valueOf("NEW")).isEqualTo(OrderStatus.NEW);
        assertThat(OrderStatus.valueOf("PENDING_REVIEW")).isEqualTo(OrderStatus.PENDING_REVIEW);
        assertThat(OrderStatus.valueOf("APPROVED")).isEqualTo(OrderStatus.APPROVED);
        assertThat(OrderStatus.valueOf("REJECTED")).isEqualTo(OrderStatus.REJECTED);
        assertThat(OrderStatus.valueOf("PAID")).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.valueOf("ALLOCATED")).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(OrderStatus.valueOf("SHIPPED")).isEqualTo(OrderStatus.SHIPPED);
        assertThat(OrderStatus.valueOf("FAILED")).isEqualTo(OrderStatus.FAILED);
    }
}
