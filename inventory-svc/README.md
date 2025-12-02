# Inventory Service

This service is responsible for handling inventory allocation for an order.

## Responsibilities

-   **Process Inventory Requests:** Listens for `inventory.requested` events from the orchestrator.
-   **Simulate Inventory Logic:** Simulates checking stock and allocating items for an order.
-   **Publish Results:** Publishes an `inventory.result` event indicating whether the allocation was successful.
-   **Idempotency:** Uses an idempotency store to prevent duplicate processing.
