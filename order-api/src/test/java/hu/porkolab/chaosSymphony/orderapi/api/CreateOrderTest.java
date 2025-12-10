package hu.porkolab.chaosSymphony.orderapi.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldCreateValidOrder() {
        CreateOrder order = new CreateOrder("customer-1", BigDecimal.valueOf(100), "USD");

        assertThat(order.customerId()).isEqualTo("customer-1");
        assertThat(order.total()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(order.currency()).isEqualTo("USD");
    }

    @Test
    void shouldFailValidationForNullCustomerId() {
        CreateOrder order = new CreateOrder(null, BigDecimal.valueOf(100), "USD");
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerId"));
    }

    @Test
    void shouldFailValidationForBlankCustomerId() {
        CreateOrder order = new CreateOrder("", BigDecimal.valueOf(100), "USD");
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationForNullTotal() {
        CreateOrder order = new CreateOrder("customer-1", null, "USD");
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("total"));
    }

    @Test
    void shouldFailValidationForZeroTotal() {
        CreateOrder order = new CreateOrder("customer-1", BigDecimal.ZERO, "USD");
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationForNegativeTotal() {
        CreateOrder order = new CreateOrder("customer-1", BigDecimal.valueOf(-10), "USD");
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailValidationForNullCurrency() {
        CreateOrder order = new CreateOrder("customer-1", BigDecimal.valueOf(100), null);
        var violations = validator.validate(order);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldAcceptMinimumValidTotal() {
        CreateOrder order = new CreateOrder("customer-1", BigDecimal.valueOf(0.01), "EUR");
        var violations = validator.validate(order);

        assertThat(violations).isEmpty();
    }
}
