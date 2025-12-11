package hu.porkolab.chaosSymphony.orderapi.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.events.OrderCreated;
import hu.porkolab.chaosSymphony.orderapi.api.CreateOrder;
import hu.porkolab.chaosSymphony.orderapi.domain.*;
import hu.porkolab.chaosSymphony.orderapi.app.FraudDetectionService.FraudCheckResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final FraudDetectionService fraudDetectionService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    
    @Transactional
    public OrderCreationResult createOrder(CreateOrder cmd) {
        UUID orderId = UUID.randomUUID();
        BigDecimal total = cmd.total().setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now(clock);

        
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.NEW)
                .total(total)
                .customerId(cmd.customerId())
                .shippingAddress(cmd.shippingAddress())
                .createdAt(now)
                .build();

        
        FraudCheckResult fraudResult = fraudDetectionService.evaluate(order);
        order.setFraudScore(fraudResult.score());

        if (fraudResult.isRejected()) {
            
            order.reject(fraudResult.reason());
            orderRepository.save(order);
            log.warn("Order {} auto-rejected due to fraud risk: {}", orderId, fraudResult.reason());
            return new OrderCreationResult(orderId, OrderStatus.REJECTED, fraudResult.reason());
        }

        if (fraudResult.requiresReview()) {
            
            order.flagForReview(fraudResult.score(), fraudResult.reason());
            orderRepository.save(order);
            log.info("Order {} flagged for manual review: {}", orderId, fraudResult.reason());
            return new OrderCreationResult(orderId, OrderStatus.PENDING_REVIEW, fraudResult.reason());
        }

        
        orderRepository.save(order);
        publishOrderCreatedEvent(order, now);

        log.info("Order {} created and published for processing", orderId);
        return new OrderCreationResult(orderId, OrderStatus.NEW, null);
    }

    
    @Transactional
    public Order approveOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Cannot approve order in status: " + order.getStatus());
        }

        order.approve();
        orderRepository.save(order);

        
        publishOrderCreatedEvent(order, Instant.now(clock));

        log.info("Order {} approved and published for processing", orderId);
        return order;
    }

    
    @Transactional
    public Order rejectOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.reject(reason);
        orderRepository.save(order);

        log.info("Order {} rejected: {}", orderId, reason);
        return order;
    }

    
    public Optional<Order> getOrder(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    private void publishOrderCreatedEvent(Order order, Instant timestamp) {
        OrderCreated eventPayload = OrderCreated.newBuilder()
                .setOrderId(order.getId().toString())
                .setTotal(order.getTotal().doubleValue())
                .setCurrency("USD")
                .setCustomerId(order.getCustomerId())
                .setShippingAddress(order.getShippingAddress())
                .build();

        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            OrderOutbox outboxEvent = OrderOutbox.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(order.getId())
                    .type(eventPayload.getClass().getSimpleName())
                    .payload(payloadJson)
                    .occurredAt(timestamp)
                    .build();
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OrderCreated event for outbox", e);
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    
    public record OrderCreationResult(
            UUID orderId,
            OrderStatus status,
            String reviewReason
    ) {
        public boolean requiresReview() {
            return status == OrderStatus.PENDING_REVIEW;
        }

        public boolean isRejected() {
            return status == OrderStatus.REJECTED;
        }
    }
}
