package hu.porkolab.chaosSymphony.orderapi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
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

    @Test
    void shouldBuildOrderWithAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Order order = Order.builder()
            .id(id)
            .status(OrderStatus.SHIPPED)
            .total(BigDecimal.valueOf(250.50))
            .createdAt(now)
            .build();

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getTotal()).isEqualTo(BigDecimal.valueOf(250.50));
        assertThat(order.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void shouldUseNoArgsConstructor() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.FAILED);
        order.setTotal(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }
}
