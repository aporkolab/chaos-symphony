package hu.porkolab.chaosSymphony.orchestrator.saga;


public enum SagaState {
    
    STARTED(SagaPhase.INITIAL),

    
    PAYMENT_PENDING(SagaPhase.PAYMENT),

    
    PAYMENT_COMPLETED(SagaPhase.PAYMENT),

    
    PAYMENT_FAILED(SagaPhase.PAYMENT),

    
    INVENTORY_PENDING(SagaPhase.INVENTORY),

    
    INVENTORY_RESERVED(SagaPhase.INVENTORY),

    
    INVENTORY_FAILED(SagaPhase.INVENTORY),

    
    SHIPPING_PENDING(SagaPhase.SHIPPING),

    
    SHIPPING_FAILED(SagaPhase.SHIPPING),

    
    COMPLETED(SagaPhase.TERMINAL),

    
    COMPENSATING(SagaPhase.COMPENSATING),

    
    COMPENSATED(SagaPhase.TERMINAL);

    private final SagaPhase phase;

    SagaState(SagaPhase phase) {
        this.phase = phase;
    }

    
    public SagaPhase getPhase() {
        return phase;
    }

    
    public boolean isFailure() {
        return this == PAYMENT_FAILED || this == INVENTORY_FAILED || this == SHIPPING_FAILED;
    }

    
    public boolean isTerminal() {
        return phase == SagaPhase.TERMINAL;
    }

    
    public boolean isCompensating() {
        return this == COMPENSATING;
    }

    
    public CompensationStrategy getCompensationStrategy() {
        return switch (this) {
            case PAYMENT_FAILED -> CompensationStrategy.CANCEL_ORDER_ONLY;
            case INVENTORY_FAILED -> CompensationStrategy.REFUND_AND_CANCEL;
            case SHIPPING_FAILED -> CompensationStrategy.FULL_COMPENSATION;
            default -> CompensationStrategy.NONE;
        };
    }

    
    public enum SagaPhase {
        INITIAL,
        PAYMENT,
        INVENTORY,
        SHIPPING,
        COMPENSATING,
        TERMINAL
    }

    
    public enum CompensationStrategy {
        
        NONE,
        
        CANCEL_ORDER_ONLY,
        
        REFUND_AND_CANCEL,
        
        FULL_COMPENSATION
    }
}
