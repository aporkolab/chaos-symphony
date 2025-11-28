package hu.porkolab.chaosSymphony.orchestrator.saga;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Entity
@Table(name = "saga_instance", indexes = {
    @Index(name = "idx_saga_state", columnList = "state"),
    @Index(name = "idx_saga_updated", columnList = "updated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_state", length = 32)
    private SagaState failedState;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "inventory_reservation_id", length = 36)
    private String inventoryReservationId;

    @Column(name = "shipping_id", length = 36)
    private String shippingId;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    
    public void transitionTo(SagaState newState) {
        this.state = newState;
    }

    
    public void fail(SagaState failedState, String reason) {
        this.failedState = failedState;
        this.failureReason = reason;
    }
}
