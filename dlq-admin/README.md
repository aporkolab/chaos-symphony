# DLQ Admin Service

This service provides a REST API for managing messages in Dead-Letter Queues (DLQs).

## Responsibilities

-   **List DLQ Topics:** Provides an endpoint to list all DLQ topics that currently contain messages.
-   **Peek Messages:** Allows an operator to view the content (headers, payload) of messages in a specific DLQ without consuming them.
-   **Replay Messages:** Provides endpoints to replay all messages from a topic or a selected list of messages. The messages are re-published to their original topic for reprocessing.
-   **Purge Messages:** (Not yet implemented) A future endpoint could allow for purging messages from a DLQ.

## API Documentation

This service provides an OpenAPI (Swagger) specification for its REST API. When the service is running, the interactive Swagger UI can be accessed at:

[/swagger-ui.html](/swagger-ui.html)
