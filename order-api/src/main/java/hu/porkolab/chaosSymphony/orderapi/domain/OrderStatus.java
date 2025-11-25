package hu.porkolab.chaosSymphony.orderapi.domain;

public enum OrderStatus {
    NEW,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    PAID,
    ALLOCATED,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    PAYMENT_FAILED,
    INVENTORY_FAILED,
    SHIPPING_FAILED,
    FAILED;

    public boolean requiresReview() {
        return this == PENDING_REVIEW;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED || this == REJECTED 
            || this == PAYMENT_FAILED || this == INVENTORY_FAILED || this == SHIPPING_FAILED;
    }

    public boolean canProceedToPayment() {
        return this == NEW || this == APPROVED;
    }
}
