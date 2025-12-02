# Shipping Service

This service is responsible for handling the shipping and fulfillment of a paid and allocated order.

## Responsibilities

-   **Process Shipping Requests:** Listens for `shipping.requested` events from the orchestrator.
-   **Simulate Shipping Logic:** Simulates the process of creating a shipment and preparing it for delivery.
-   **Publish Results:** Publishes a `shipping.result` event, which is the final step in the successful order saga.
-   **Idempotency:** Uses an idempotency store to prevent duplicate processing.
