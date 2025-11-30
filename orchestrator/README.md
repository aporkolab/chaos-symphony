# Orchestrator Service

This service implements the Saga orchestration pattern for the order processing workflow.

## Responsibilities

-   **State Machine:** Manages the state of an order as it progresses through the system (`NEW` -> `PAID` -> `ALLOCATED` -> `SHIPPED` / `FAILED`).
-   **Event-Driven:** It listens for events from other services (e.g., `OrderCreated`, `PaymentResult`, `InventoryResult`, `ShippingResult`) and sends out new command events to trigger the next step in the saga.
-   **Canary Release Logic:** Contains the logic to split traffic between the primary `payment-svc` and the `payment-svc-canary` based on a configured percentage. This is controlled dynamically via an actuator endpoint.

## Listened-To Topics

-   `order.created`
-   `payment.result`
-   `inventory.result`
-   `shipping.result`

## Published-To Topics

-   `payment.requested`
-   `payment.requested.canary`
-   `inventory.requested`
-   `shipping.requested`
