package hu.porkolab.chaosSymphony.orderapi.api;

import hu.porkolab.chaosSymphony.orderapi.app.OrderService;
import hu.porkolab.chaosSymphony.orderapi.app.OrderService.OrderCreationResult;
import hu.porkolab.chaosSymphony.orderapi.api.OrderController.OrderResponse;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("OrderController")
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock OrderRepository orderRepository;

    private OrderController controller;

    @BeforeEach
    void setup() {
        controller = new OrderController(orderService, orderRepository);
    }

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrderTests {

        @Test
        @DisplayName("Should accept low-risk order with 202 status")
        void shouldAcceptLowRiskOrder() {
            UUID orderId = UUID.randomUUID();
            CreateOrder command = new CreateOrder("customer-1", BigDecimal.valueOf(100), "USD");
            OrderCreationResult result = new OrderCreationResult(orderId, OrderStatus.NEW, null);

            when(orderService.createOrder(command)).thenReturn(result);

            var response = controller.createOrder(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody().orderId()).isEqualTo(orderId);
            assertThat(response.getBody().status()).isEqualTo(OrderStatus.NEW);
            assertThat(response.getBody().reviewReason()).isNull();
        }

        @Test
        @DisplayName("Should flag high-value order for review with 200 status")
        void shouldFlagHighValueOrder() {
            UUID orderId = UUID.randomUUID();
            CreateOrder command = new CreateOrder("customer-1", BigDecimal.valueOf(2000), "USD");
            OrderCreationResult result = new OrderCreationResult(
                    orderId, OrderStatus.PENDING_REVIEW, "High value order: $2000.00");

            when(orderService.createOrder(command)).thenReturn(result);

            var response = controller.createOrder(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING_REVIEW);
            assertThat(response.getBody().reviewReason()).contains("High value");
        }

        @Test
        @DisplayName("Should reject high-risk order with 200 status")
        void shouldRejectHighRiskOrder() {
            UUID orderId = UUID.randomUUID();
            CreateOrder command = new CreateOrder("fraud-customer", BigDecimal.valueOf(500), "USD");
            OrderCreationResult result = new OrderCreationResult(
                    orderId, OrderStatus.REJECTED, "Auto-rejected: fraud score 95");

            when(orderService.createOrder(command)).thenReturn(result);

            var response = controller.createOrder(command);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(OrderStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("GET /api/orders")
    class ListOrdersTests {

        @Test
        @DisplayName("Should return all orders sorted by creation date")
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
        @DisplayName("Should return pending review orders")
        void shouldGetPendingReviewOrders() {
            Order pendingOrder = Order.builder()
                    .id(UUID.randomUUID())
                    .status(OrderStatus.PENDING_REVIEW)
                    .total(BigDecimal.valueOf(1500))
                    .reviewReason("High value")
                    .createdAt(Instant.now())
                    .build();

            when(orderRepository.findByStatus(OrderStatus.PENDING_REVIEW))
                    .thenReturn(List.of(pendingOrder));

            List<Order> orders = controller.getPendingReviewOrders();

            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Should return order when found")
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
        @DisplayName("Should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            var response = controller.getOrderById(orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{id}/approve")
    class ApproveOrderTests {

        @Test
        @DisplayName("Should approve pending order")
        void shouldApproveOrder() {
            UUID orderId = UUID.randomUUID();
            Order approvedOrder = Order.builder()
                    .id(orderId)
                    .status(OrderStatus.APPROVED)
                    .total(BigDecimal.valueOf(1500))
                    .createdAt(Instant.now())
                    .build();

            when(orderService.approveOrder(orderId)).thenReturn(approvedOrder);

            var response = controller.approveOrder(orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 404 when order not found")
        void shouldReturn404WhenApprovingNonExistentOrder() {
            UUID orderId = UUID.randomUUID();
            when(orderService.approveOrder(orderId))
                    .thenThrow(new IllegalArgumentException("Order not found"));

            var response = controller.approveOrder(orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return 400 when order cannot be approved")
        void shouldReturn400WhenOrderCannotBeApproved() {
            UUID orderId = UUID.randomUUID();
            when(orderService.approveOrder(orderId))
                    .thenThrow(new IllegalStateException("Order is not in PENDING_REVIEW status"));

            var response = controller.approveOrder(orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{id}/reject")
    class RejectOrderTests {

        @Test
        @DisplayName("Should reject order with reason")
        void shouldRejectOrder() {
            UUID orderId = UUID.randomUUID();
            Order rejectedOrder = Order.builder()
                    .id(orderId)
                    .status(OrderStatus.REJECTED)
                    .reviewReason("Fraud confirmed")
                    .total(BigDecimal.valueOf(1500))
                    .createdAt(Instant.now())
                    .build();

            when(orderService.rejectOrder(eq(orderId), anyString())).thenReturn(rejectedOrder);

            var request = new OrderController.RejectOrderRequest("Fraud confirmed");
            var response = controller.rejectOrder(orderId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(orderService).rejectOrder(orderId, "Fraud confirmed");
        }

        @Test
        @DisplayName("Should reject order with default reason when none provided")
        void shouldRejectOrderWithDefaultReason() {
            UUID orderId = UUID.randomUUID();
            Order rejectedOrder = Order.builder()
                    .id(orderId)
                    .status(OrderStatus.REJECTED)
                    .reviewReason("Rejected during manual review")
                    .total(BigDecimal.valueOf(1500))
                    .createdAt(Instant.now())
                    .build();

            when(orderService.rejectOrder(eq(orderId), anyString())).thenReturn(rejectedOrder);

            var response = controller.rejectOrder(orderId, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(orderService).rejectOrder(orderId, "Rejected during manual review");
        }

        @Test
        @DisplayName("Should return 404 when order not found")
        void shouldReturn404WhenRejectingNonExistentOrder() {
            UUID orderId = UUID.randomUUID();
            when(orderService.rejectOrder(eq(orderId), anyString()))
                    .thenThrow(new IllegalArgumentException("Order not found"));

            var response = controller.rejectOrder(orderId, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /api/orders/start (demo endpoint)")
    class StartOrderTests {

        @Test
        @DisplayName("Should start order with specified amount")
        void shouldStartOrder() {
            UUID orderId = UUID.randomUUID();
            OrderCreationResult result = new OrderCreationResult(orderId, OrderStatus.NEW, null);
            when(orderService.createOrder(any(CreateOrder.class))).thenReturn(result);

            var response = controller.startOrder(BigDecimal.valueOf(150), "customer-456");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().orderId()).isEqualTo(orderId);
        }
    }
}
