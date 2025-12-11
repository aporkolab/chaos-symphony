package hu.porkolab.chaosSymphony.orderapi.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.events.OrderCreated;
import hu.porkolab.chaosSymphony.orderapi.api.CreateOrder;
import hu.porkolab.chaosSymphony.orderapi.app.OrderService.OrderCreationResult;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderOutboxRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("OrderService Integration Tests")
class OrderServiceIntegrationTest {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    OrderServiceIntegrationTest(
            OrderService orderService,
            OrderRepository orderRepository,
            OrderOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void cleanup() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Nested
    @DisplayName("Low-risk order creation")
    class LowRiskOrderCreation {

        @Test
        @DisplayName("Should save order and outbox event atomically")
        void createOrder_shouldSaveOrderAndOutboxEventAtomically() throws Exception {
            
            String customerId = "test-customer-123";
            CreateOrder command = new CreateOrder(customerId, BigDecimal.valueOf(99.99), "USD", null);

            
            OrderCreationResult result = orderService.createOrder(command);

            
            assertNotNull(result.orderId());
            assertThat(result.status()).isEqualTo(OrderStatus.NEW);
            assertThat(result.requiresReview()).isFalse();

            
            Order savedOrder = orderRepository.findById(result.orderId()).orElseThrow();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(savedOrder.getCustomerId()).isEqualTo(customerId);

            
            var outboxEvents = outboxRepository.findAll();
            assertEquals(1, outboxEvents.size());

            var outboxEvent = outboxEvents.get(0);
            assertEquals(result.orderId(), outboxEvent.getAggregateId());
            assertEquals("OrderCreated", outboxEvent.getType());

            
            OrderCreated payload = objectMapper.readValue(outboxEvent.getPayload(), OrderCreated.class);
            assertEquals(result.orderId().toString(), payload.getOrderId());
            assertEquals(customerId, payload.getCustomerId());
            assertEquals(99.99, payload.getTotal());
        }
    }

    @Nested
    @DisplayName("High-value order creation")
    class HighValueOrderCreation {

        @Test
        @DisplayName("Should flag high-value order for review and NOT publish event")
        void shouldFlagHighValueOrder() {
            
            String customerId = "high-value-customer";
            CreateOrder command = new CreateOrder(customerId, BigDecimal.valueOf(1500.00), "USD", null);

            
            OrderCreationResult result = orderService.createOrder(command);

            
            assertThat(result.status()).isEqualTo(OrderStatus.PENDING_REVIEW);
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reviewReason()).contains("High value");

            
            Order savedOrder = orderRepository.findById(result.orderId()).orElseThrow();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
            assertThat(savedOrder.getFraudScore()).isNotNull();

            
            var outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("Order approval flow")
    class OrderApprovalFlow {

        @Test
        @DisplayName("Should approve pending order and publish event")
        void shouldApproveOrderAndPublishEvent() throws Exception {
            
            CreateOrder command = new CreateOrder("customer", BigDecimal.valueOf(2000.00), "USD", null);
            OrderCreationResult createResult = orderService.createOrder(command);
            assertThat(createResult.status()).isEqualTo(OrderStatus.PENDING_REVIEW);
            assertThat(outboxRepository.findAll()).isEmpty();

            
            Order approvedOrder = orderService.approveOrder(createResult.orderId());

            
            assertThat(approvedOrder.getStatus()).isEqualTo(OrderStatus.APPROVED);

            
            var outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).hasSize(1);

            OrderCreated payload = objectMapper.readValue(
                    outboxEvents.get(0).getPayload(), OrderCreated.class);
            assertThat(payload.getOrderId()).isEqualTo(createResult.orderId().toString());
        }

        @Test
        @DisplayName("Should reject pending order without publishing event")
        void shouldRejectOrderWithoutPublishingEvent() {
            
            CreateOrder command = new CreateOrder("customer", BigDecimal.valueOf(3000.00), "USD", null);
            OrderCreationResult createResult = orderService.createOrder(command);

            
            Order rejectedOrder = orderService.rejectOrder(createResult.orderId(), "Fraud confirmed");

            
            assertThat(rejectedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(rejectedOrder.getReviewReason()).isEqualTo("Fraud confirmed");

            
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Should fail when approving non-pending order")
        void shouldFailWhenApprovingNonPendingOrder() {
            
            CreateOrder command = new CreateOrder("customer", BigDecimal.valueOf(50.00), "USD", null);
            OrderCreationResult createResult = orderService.createOrder(command);
            assertThat(createResult.status()).isEqualTo(OrderStatus.NEW);

            
            assertThrows(IllegalStateException.class, () ->
                    orderService.approveOrder(createResult.orderId()));
        }
    }
}
