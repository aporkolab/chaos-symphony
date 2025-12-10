package hu.porkolab.chaosSymphony.orderapi.api;

import hu.porkolab.chaosSymphony.orderapi.app.OrderService;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock OrderRepository orderRepository;

    private OrderController controller;

    @BeforeEach
    void setup() {
        controller = new OrderController(orderService, orderRepository);
    }

    @Test
    void shouldCreateOrder() {
        UUID orderId = UUID.randomUUID();
        CreateOrder command = new CreateOrder("customer-1", BigDecimal.valueOf(100), "USD");

        when(orderService.createOrder(command)).thenReturn(orderId);

        var response = controller.createOrder(command);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(orderId);
        verify(orderService).createOrder(command);
    }

    @Test
    void shouldGetAllOrders() {
        Order order1 = Order.builder()
            .id(UUID.randomUUID())
            .status(OrderStatus.NEW)
            .total(BigDecimal.valueOf(100))
            .createdAt(Instant.now())
            .build();
        Order order2 = Order.builder()
            .id(UUID.randomUUID())
            .status(OrderStatus.PAID)
            .total(BigDecimal.valueOf(200))
            .createdAt(Instant.now())
            .build();

        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(order1, order2));

        List<Order> orders = controller.getAllOrders();

        assertThat(orders).hasSize(2);
        verify(orderRepository).findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void shouldGetOrderById() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
            .id(orderId)
            .status(OrderStatus.NEW)
            .total(BigDecimal.valueOf(100))
            .createdAt(Instant.now())
            .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var response = controller.getOrderById(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(order);
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        var response = controller.getOrderById(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldStartOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(any(CreateOrder.class))).thenReturn(orderId);

        var response = controller.startOrder(BigDecimal.valueOf(150), "customer-456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Order started:");
        assertThat(response.getBody()).contains(orderId.toString());
    }

    @Test
    void shouldStartOrderWithDefaultValues() {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(any(CreateOrder.class))).thenReturn(orderId);

        var response = controller.startOrder(BigDecimal.valueOf(100.0), "customer-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orderService).createOrder(any(CreateOrder.class));
    }

    @Test
    void shouldStartOrderWithValidUuidId() {
        UUID orderId = UUID.randomUUID();
        String uuidString = orderId.toString();
        when(orderService.createOrder(any(CreateOrder.class))).thenReturn(orderId);

        var response = controller.startOrderWithId(uuidString, BigDecimal.valueOf(200));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Order started with ID:");
    }

    @Test
    void shouldStartOrderWithInvalidUuidFallback() {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(any(CreateOrder.class))).thenReturn(orderId);

        var response = controller.startOrderWithId("not-a-uuid", BigDecimal.valueOf(300));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Order started:");
        assertThat(response.getBody()).contains("ref: not-a-uuid");
    }

    @Test
    void shouldStartOrderWithBreakMeId() {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(any(CreateOrder.class))).thenReturn(orderId);

        var response = controller.startOrderWithId("BREAK-ME", BigDecimal.valueOf(100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Order started with ID:");
    }
}
