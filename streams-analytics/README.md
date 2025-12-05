# Streams Analytics Service

This service uses Kafka Streams to perform real-time analysis on the event streams.

## Responsibilities

-   **Real-time Metrics:** Calculates windowed metrics such as throughput and processing latency (`p95`). These metrics are exposed to Prometheus.
-   **Time-Travel Replay:** Exposes a REST API endpoint (`/api/replay`) that allows an operator to reset a consumer group's offsets to a specific point in time. This is used to re-process events from a certain period, for example, after a bug fix.
-   **Status Aggregation:** Tracks the status of orders by consuming all relevant events.
