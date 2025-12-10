package hu.porkolab.chaosSymphony.orderapi.api;

import hu.porkolab.chaosSymphony.orderapi.app.OrderService;
import hu.porkolab.chaosSymphony.orderapi.app.OrderService.OrderCreationResult;
import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderRepository;
import hu.porkolab.chaosSymphony.orderapi.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management with fraud detection")
public class OrderController {
    private final OrderService service;
    private final OrderRepository repository;

    @PostMapping
    @Operation(summary = "Create a new order",
            description = "Creates an order with automatic fraud detection. " +
                    "High-risk orders are flagged for manual review.")
    @ApiResponse(responseCode = "202", description = "Order accepted for processing")
    @ApiResponse(responseCode = "200", description = "Order flagged for review or rejected")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid CreateOrder command) {
        OrderCreationResult result = service.createOrder(command);

        OrderResponse response = new OrderResponse(
                result.orderId(),
                result.status(),
                result.reviewReason()
        );

        if (result.isRejected()) {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
        if (result.requiresReview()) {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping
    @Operation(summary = "List all orders", description = "Returns all orders sorted by creation date (newest first)")
    public List<Order> getAllOrders() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @GetMapping("/pending-review")
    @Operation(summary = "List orders pending review",
            description = "Returns orders flagged for manual fraud review")
    public List<Order> getPendingReviewOrders() {
        return repository.findByStatus(OrderStatus.PENDING_REVIEW);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<Order> getOrderById(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve order after review",
            description = "Approves a flagged order, triggering the payment saga")
    @ApiResponse(responseCode = "200", description = "Order approved and processing started")
    @ApiResponse(responseCode = "400", description = "Order cannot be approved (wrong status)")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<?> approveOrder(@PathVariable UUID id) {
        try {
            Order order = service.approveOrder(id);
            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "status", order.getStatus(),
                    "message", "Order approved and submitted for processing"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject order after review",
            description = "Rejects a flagged order (fraud confirmed)")
    @ApiResponse(responseCode = "200", description = "Order rejected")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<?> rejectOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectOrderRequest request) {
        try {
            String reason = request != null && request.reason() != null
                    ? request.reason()
                    : "Rejected during manual review";
            Order order = service.rejectOrder(id, reason);
            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "status", order.getStatus(),
                    "reason", order.getReviewReason()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    
    @PostMapping("/start")
    @Operation(summary = "Quick order creation for demo",
            description = "Creates an order with specified amount. Use amounts > $1000 to trigger fraud review.")
    public ResponseEntity<OrderResponse> startOrder(
            @RequestParam(defaultValue = "100.0") BigDecimal amount,
            @RequestParam(defaultValue = "customer-123") String customerId) {
        CreateOrder command = new CreateOrder(customerId, amount, "USD");
        OrderCreationResult result = service.createOrder(command);
        return ResponseEntity.ok(new OrderResponse(
                result.orderId(),
                result.status(),
                result.reviewReason()
        ));
    }

    
    public record OrderResponse(
            UUID orderId,
            OrderStatus status,
            String reviewReason
    ) {}

    
    public record RejectOrderRequest(String reason) {}
}
