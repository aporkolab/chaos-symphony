# Order API Service

This service is the main entry point for creating new orders in the Chaos Symphony system.

## Responsibilities

-   **Exposes a REST API:** Provides an endpoint for external clients to submit new orders.
-   **Initiates the Saga:** Upon receiving a new order request, it saves the order to its local database and creates an `OrderCreated` event.
-   **Transactional Outbox:** Writes the outgoing `OrderCreated` event to an `order_outbox` table in the same transaction as the `orders` table. This guarantees that the event will be published if and only if the order is successfully saved. Debezium then streams this event to Kafka.

## Endpoints

-   `POST /api/orders/start`: Creates a new order with a random amount and starts the orchestration flow.
-   `POST /api/orders`: Creates a new order with the given payload.
-   `GET /api/orders/{id}`: Retrieves the status of a specific order.
-   `GET /actuator/health`: Standard Spring Boot health check.

## API Documentation

This service provides an OpenAPI (Swagger) specification for its REST API. When the service is running, the interactive Swagger UI can be accessed at:

[/swagger-ui.html](/swagger-ui.html)
