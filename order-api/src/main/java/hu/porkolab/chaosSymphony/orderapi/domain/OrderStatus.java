package hu.porkolab.chaosSymphony.orderapi.domain;


public enum OrderStatus {
    
    NEW,

    
    PENDING_REVIEW,

    
    APPROVED,

    
    REJECTED,

    
    PAID,

    
    ALLOCATED,

    
    SHIPPED,

    
    FAILED;

    
    public boolean requiresReview() {
        return this == PENDING_REVIEW;
    }

    
    public boolean isTerminal() {
        return this == SHIPPED || this == FAILED || this == REJECTED;
    }

    
    public boolean canProceedToPayment() {
        return this == NEW || this == APPROVED;
    }
}
