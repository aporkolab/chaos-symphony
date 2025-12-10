package hu.porkolab.chaosSymphony.orderapi.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    
    @Column(precision = 5, scale = 2)
    private BigDecimal fraudScore;

    
    @Column(length = 500)
    private String reviewReason;

    
    @Column(length = 64)
    private String customerId;

    @PrePersist
    void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
        if (status == null)
            status = OrderStatus.NEW;
    }

    
    public void flagForReview(BigDecimal fraudScore, String reason) {
        this.fraudScore = fraudScore;
        this.reviewReason = reason;
        this.status = OrderStatus.PENDING_REVIEW;
    }

    
    public void approve() {
        if (this.status != OrderStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Can only approve orders in PENDING_REVIEW status");
        }
        this.status = OrderStatus.APPROVED;
    }

    
    public void reject(String reason) {
        this.reviewReason = reason;
        this.status = OrderStatus.REJECTED;
    }
}
