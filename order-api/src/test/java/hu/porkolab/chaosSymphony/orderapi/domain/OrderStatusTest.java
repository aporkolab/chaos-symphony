package hu.porkolab.chaosSymphony.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void shouldHaveAllExpectedValues() {
        assertThat(OrderStatus.values()).containsExactly(
            OrderStatus.NEW,
            OrderStatus.PAID,
            OrderStatus.ALLOCATED,
            OrderStatus.SHIPPED,
            OrderStatus.FAILED
        );
    }

    @Test
    void shouldConvertFromString() {
        assertThat(OrderStatus.valueOf("NEW")).isEqualTo(OrderStatus.NEW);
        assertThat(OrderStatus.valueOf("PAID")).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.valueOf("ALLOCATED")).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(OrderStatus.valueOf("SHIPPED")).isEqualTo(OrderStatus.SHIPPED);
        assertThat(OrderStatus.valueOf("FAILED")).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void shouldHaveCorrectName() {
        assertThat(OrderStatus.NEW.name()).isEqualTo("NEW");
        assertThat(OrderStatus.FAILED.name()).isEqualTo("FAILED");
    }
}
