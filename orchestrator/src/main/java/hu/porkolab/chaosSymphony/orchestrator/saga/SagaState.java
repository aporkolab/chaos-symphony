package hu.porkolab.chaosSymphony.orchestrator.saga;


public enum SagaState {
    
    STARTED,

    
    PAYMENT_PENDING,

    
    PAYMENT_COMPLETED,

    
    INVENTORY_RESERVED,

    
    COMPLETED,

    
    PAYMENT_FAILED,

    
    INVENTORY_FAILED,

    
    SHIPPING_FAILED,

    
    COMPENSATING,

    
    COMPENSATED
}
