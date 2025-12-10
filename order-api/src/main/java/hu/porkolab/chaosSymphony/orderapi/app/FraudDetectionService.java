package hu.porkolab.chaosSymphony.orderapi.app;

import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fraud detection service implementing rule-based risk scoring.
 *
 * <p>Business Rules:
 * <ul>
 *   <li>Orders over $1000 → automatic review</li>
 *   <li>Fraud score > 70 → automatic review</li>
 *   <li>Fraud score > 90 → automatic rejection</li>
 *   <li>Multiple orders from same customer in short time → elevated risk</li>
 * </ul>
 *
 * <p>In production, this would integrate with external fraud detection APIs
 * (e.g., Stripe Radar, Sift, or custom ML models).
 */
@Slf4j
@Service
public class FraudDetectionService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal REVIEW_SCORE_THRESHOLD = new BigDecimal("70.00");
    private static final BigDecimal REJECT_SCORE_THRESHOLD = new BigDecimal("90.00");

    private final Counter ordersScanned;
    private final Counter ordersFlagged;
    private final Counter ordersAutoRejected;

    /**
     * Simple in-memory velocity tracking.
     * In production: use Redis with TTL or a dedicated fraud service.
     */
    private final Map<String, List<Long>> customerOrderTimestamps = new ConcurrentHashMap<>();

    @Value("${fraud.velocity.window.minutes:60}")
    private int velocityWindowMinutes;

    @Value("${fraud.velocity.max.orders:5}")
    private int velocityMaxOrders;

    public FraudDetectionService(MeterRegistry meterRegistry) {
        this.ordersScanned = meterRegistry.counter("fraud.orders.scanned");
        this.ordersFlagged = meterRegistry.counter("fraud.orders.flagged");
        this.ordersAutoRejected = meterRegistry.counter("fraud.orders.auto_rejected");
    }

    /**
     * Evaluates fraud risk for an order.
     *
     * @param order The order to evaluate
     * @return FraudCheckResult containing score and recommended action
     */
    public FraudCheckResult evaluate(Order order) {
        ordersScanned.increment();

        BigDecimal score = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();

        // Rule 1: High-value order check
        if (order.getTotal().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            BigDecimal valueScore = calculateValueRiskScore(order.getTotal());
            score = score.add(valueScore);
            reasons.add(String.format("High value order: $%.2f (score +%.1f)",
                    order.getTotal(), valueScore));
        }

        // Rule 2: Velocity check (multiple orders in short time)
        if (order.getCustomerId() != null) {
            BigDecimal velocityScore = calculateVelocityScore(order.getCustomerId());
            if (velocityScore.compareTo(BigDecimal.ZERO) > 0) {
                score = score.add(velocityScore);
                reasons.add(String.format("Velocity anomaly detected (score +%.1f)", velocityScore));
            }
            recordOrderTimestamp(order.getCustomerId());
        }

        // Rule 3: Round number detection (common in fraud)
        if (isRoundNumber(order.getTotal())) {
            score = score.add(new BigDecimal("15.00"));
            reasons.add("Suspiciously round amount (score +15)");
        }

        // Rule 4: Time-based risk (orders at unusual hours get slight bump)
        // Simplified: in production, use customer's timezone and historical patterns
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 1 && hour <= 5) {
            score = score.add(new BigDecimal("10.00"));
            reasons.add("Off-hours order (score +10)");
        }

        // Cap score at 100
        score = score.min(new BigDecimal("100.00"));

        FraudAction action = determineAction(score, order.getTotal());

        if (action == FraudAction.REJECT) {
            ordersAutoRejected.increment();
            log.warn("Order {} auto-rejected. Score: {}, Reasons: {}",
                    order.getId(), score, reasons);
        } else if (action == FraudAction.REVIEW) {
            ordersFlagged.increment();
            log.info("Order {} flagged for review. Score: {}, Reasons: {}",
                    order.getId(), score, reasons);
        } else {
            log.debug("Order {} passed fraud check. Score: {}", order.getId(), score);
        }

        return new FraudCheckResult(
                score.setScale(2, RoundingMode.HALF_UP),
                action,
                String.join("; ", reasons)
        );
    }

    private BigDecimal calculateValueRiskScore(BigDecimal total) {
        // Progressive scoring: higher value = higher risk
        // $1000-2000: +20, $2000-5000: +35, $5000+: +50
        if (total.compareTo(new BigDecimal("5000")) >= 0) {
            return new BigDecimal("50.00");
        } else if (total.compareTo(new BigDecimal("2000")) >= 0) {
            return new BigDecimal("35.00");
        } else {
            return new BigDecimal("20.00");
        }
    }

    private BigDecimal calculateVelocityScore(String customerId) {
        List<Long> timestamps = customerOrderTimestamps.get(customerId);
        if (timestamps == null || timestamps.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long windowStart = System.currentTimeMillis() - (velocityWindowMinutes * 60 * 1000L);
        long recentOrders = timestamps.stream()
                .filter(ts -> ts > windowStart)
                .count();

        if (recentOrders >= velocityMaxOrders) {
            return new BigDecimal("40.00"); // High velocity = significant risk
        } else if (recentOrders >= velocityMaxOrders / 2) {
            return new BigDecimal("20.00"); // Moderate velocity
        }
        return BigDecimal.ZERO;
    }

    private void recordOrderTimestamp(String customerId) {
        customerOrderTimestamps
                .computeIfAbsent(customerId, k -> new ArrayList<>())
                .add(System.currentTimeMillis());

        // Cleanup old entries (simple housekeeping)
        long windowStart = System.currentTimeMillis() - (velocityWindowMinutes * 60 * 1000L);
        customerOrderTimestamps.get(customerId).removeIf(ts -> ts < windowStart);
    }

    private boolean isRoundNumber(BigDecimal amount) {
        // Check if amount is a "round" number (ends in 00, 50, or 000)
        BigDecimal remainder = amount.remainder(new BigDecimal("100"));
        return remainder.compareTo(BigDecimal.ZERO) == 0
                || remainder.compareTo(new BigDecimal("50")) == 0;
    }

    private FraudAction determineAction(BigDecimal score, BigDecimal total) {
        // Auto-reject if score is extremely high
        if (score.compareTo(REJECT_SCORE_THRESHOLD) >= 0) {
            return FraudAction.REJECT;
        }

        // Review if score is elevated OR if high-value regardless of score
        if (score.compareTo(REVIEW_SCORE_THRESHOLD) >= 0
                || total.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return FraudAction.REVIEW;
        }

        return FraudAction.APPROVE;
    }

    /**
     * Result of fraud evaluation.
     */
    public record FraudCheckResult(
            BigDecimal score,
            FraudAction action,
            String reason
    ) {
        public boolean requiresReview() {
            return action == FraudAction.REVIEW;
        }

        public boolean isRejected() {
            return action == FraudAction.REJECT;
        }
    }

    /**
     * Recommended action based on fraud evaluation.
     */
    public enum FraudAction {
        /** Order can proceed normally */
        APPROVE,
        /** Order requires manual review */
        REVIEW,
        /** Order should be automatically rejected */
        REJECT
    }
}
