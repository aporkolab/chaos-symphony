package hu.porkolab.chaosSymphony.orderapi.app;

import hu.porkolab.chaosSymphony.orderapi.domain.Order;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
@Service
public class FraudDetectionService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal REVIEW_SCORE_THRESHOLD = new BigDecimal("70.00");
    private static final BigDecimal REJECT_SCORE_THRESHOLD = new BigDecimal("90.00");

    private final Counter ordersScanned;
    private final Counter ordersFlagged;
    private final Counter ordersAutoRejected;

    
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

    
    public FraudCheckResult evaluate(Order order) {
        ordersScanned.increment();

        BigDecimal score = BigDecimal.ZERO;
        List<String> reasons = new CopyOnWriteArrayList<>();

        
        if (order.getTotal().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            BigDecimal valueScore = calculateValueRiskScore(order.getTotal());
            score = score.add(valueScore);
            reasons.add(String.format("High value order: $%.2f (score +%.1f)",
                    order.getTotal(), valueScore));
        }

        
        if (order.getCustomerId() != null) {
            BigDecimal velocityScore = calculateVelocityScore(order.getCustomerId());
            if (velocityScore.compareTo(BigDecimal.ZERO) > 0) {
                score = score.add(velocityScore);
                reasons.add(String.format("Velocity anomaly detected (score +%.1f)", velocityScore));
            }
            recordOrderTimestamp(order.getCustomerId());
        }

        
        if (isRoundNumber(order.getTotal())) {
            score = score.add(new BigDecimal("15.00"));
            reasons.add("Suspiciously round amount (score +15)");
        }

        
        
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 1 && hour <= 5) {
            score = score.add(new BigDecimal("10.00"));
            reasons.add("Off-hours order (score +10)");
        }

        
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
            return new BigDecimal("40.00"); 
        } else if (recentOrders >= velocityMaxOrders / 2) {
            return new BigDecimal("20.00"); 
        }
        return BigDecimal.ZERO;
    }

    private void recordOrderTimestamp(String customerId) {
        customerOrderTimestamps
                .computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());

        
        long windowStart = System.currentTimeMillis() - (velocityWindowMinutes * 60 * 1000L);
        customerOrderTimestamps.get(customerId).removeIf(ts -> ts < windowStart);
    }

    private boolean isRoundNumber(BigDecimal amount) {
        
        BigDecimal remainder = amount.remainder(new BigDecimal("100"));
        return remainder.compareTo(BigDecimal.ZERO) == 0
                || remainder.compareTo(new BigDecimal("50")) == 0;
    }

    private FraudAction determineAction(BigDecimal score, BigDecimal total) {
        
        if (score.compareTo(REJECT_SCORE_THRESHOLD) >= 0) {
            return FraudAction.REJECT;
        }

        
        if (score.compareTo(REVIEW_SCORE_THRESHOLD) >= 0
                || total.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return FraudAction.REVIEW;
        }

        return FraudAction.APPROVE;
    }

    
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

    
    public enum FraudAction {
        
        APPROVE,
        
        REVIEW,
        
        REJECT
    }
}
