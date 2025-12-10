package hu.porkolab.chaosSymphony.orderapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order")
class OrderTest {

    @Nested
    @DisplayName("onCreate lifecycle")
    class OnCreateLifecycle {

        @Test
        @DisplayName("Should set default status to NEW on pre-persist")
        void shouldSetDefaultStatusOnPrePersist() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .total(BigDecimal.valueOf(100))
                    .build();

            order.onCreate();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(order.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not override existing status")
        void shouldNotOverrideExistingStatus() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.PAID)
                    .total(BigDecimal.valueOf(100))
                    .build();

            order.onCreate();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("Should not override existing createdAt")
        void shouldNotOverrideExistingCreatedAt() {
            Instant existingTime = Instant.parse("2024-01-01T10:00:00Z");
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .total(BigDecimal.valueOf(100))
                    .createdAt(existingTime)
                    .build();

            order.onCreate();

            assertThat(order.getCreatedAt()).isEqualTo(existingTime);
        }
    }

    @Nested
    @DisplayName("Fraud review flow")
    class FraudReviewFlow {

        @Test
        @DisplayName("Should flag order for review with fraud score and reason")
        void shouldFlagOrderForReview() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.NEW)
                    .total(BigDecimal.valueOf(1500))
                    .build();

            order.flagForReview(new BigDecimal("75.50"), "High value order");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
            assertThat(order.getFraudScore()).isEqualTo(new BigDecimal("75.50"));
            assertThat(order.getReviewReason()).isEqualTo("High value order");
        }

        @Test
        @DisplayName("Should approve order in PENDING_REVIEW status")
        void shouldApproveOrder() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.PENDING_REVIEW)
                    .total(BigDecimal.valueOf(1500))
                    .fraudScore(new BigDecimal("60.00"))
                    .reviewReason("High value order")
                    .build();

            order.approve();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        }

        @Test
        @DisplayName("Should throw when approving non-pending order")
        void shouldThrowWhenApprovingNonPendingOrder() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.NEW)
                    .total(BigDecimal.valueOf(100))
                    .build();

            assertThatThrownBy(order::approve)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING_REVIEW");
        }

        @Test
        @DisplayName("Should reject order with reason")
        void shouldRejectOrder() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.PENDING_REVIEW)
                    .total(BigDecimal.valueOf(1500))
                    .build();

            order.reject("Fraud confirmed - stolen card");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.getReviewReason()).isEqualTo("Fraud confirmed - stolen card");
        }
    }

    @Nested
    @DisplayName("Builder and accessors")
    class BuilderAndAccessors {

        @Test
        @DisplayName("Should build order with all fields")
        void shouldBuildOrderWithAllFields() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            Order order = Order.builder()
                    .id(id)
                    .status(OrderStatus.SHIPPED)
                    .total(BigDecimal.valueOf(250.50))
                    .customerId("customer-123")
                    .fraudScore(new BigDecimal("15.00"))
                    .reviewReason(null)
                    .createdAt(now)
                    .build();

            assertThat(order.getId()).isEqualTo(id);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.getTotal()).isEqualTo(BigDecimal.valueOf(250.50));
            assertThat(order.getCustomerId()).isEqualTo("customer-123");
            assertThat(order.getFraudScore()).isEqualTo(new BigDecimal("15.00"));
            assertThat(order.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should use no-args constructor and setters")
        void shouldUseNoArgsConstructor() {
            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.FAILED);
            order.setTotal(BigDecimal.TEN);
            order.setCustomerId("cust-456");
            order.setCreatedAt(Instant.now());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getCustomerId()).isEqualTo("cust-456");
        }
    }
}
