# Developer Reference

This document contains useful information, topic names, and commands for developing and debugging the Chaos Symphony project.

## Kafka Topics

The following topics are used in the Saga workflow:

| Topic Name                  | Description                                            | Producer(s)         | Consumer(s)                 |
| --------------------------- | ------------------------------------------------------ | ------------------- | --------------------------- |
| `order.created`             | A new order has been created and needs processing.     | Debezium (via Outbox) | `orchestrator`              |
| `payment.requested`         | A new order requires payment processing.               | `orchestrator`      | `payment-svc`               |
| `payment.requested.canary`  | Payment requests routed to canary service.            | `orchestrator`      | `payment-svc-canary`        |
| `payment.result`            | The result of the payment processing (success/failure). | `payment-svc`       | `orchestrator`              |
| `inventory.requested`       | An order requires inventory allocation.                | `orchestrator`      | `inventory-svc`             |
| `inventory.result`          | The result of inventory allocation.                    | `inventory-svc`     | `orchestrator`              |
| `shipping.requested`        | An order is ready for shipping.                        | `orchestrator`      | `shipping-svc`              |
| `shipping.result`           | The result of the shipping process.                    | `shipping-svc`      | `orchestrator`              |
| `analytics.payment.status`  | Topic for real-time analytics.                         | `orchestrator`      | `streams-analytics`         |
| `*.DLT`                     | Dead-Letter Topics for failed messages.                | Spring Kafka Retry  | `dlq-admin`                 |

---



## Debugging Kafka with `kcat`



`kcat` (formerly `kafkacat`) is an invaluable tool for inspecting Kafka topics from the command line.

```bash

# Consume and print the last 5 messages from the payment.result topic
kcat -b localhost:29092 -t payment.result -C -o -5 -q

# Consume messages from a DLT in JSON format
kcat -b localhost:29092 -t inventory.requested.DLT -C -o -1 -q -J | jq .

# Produce a raw message to a topic (for testing consumers)
echo "{\"orderId\":\"test-123\", \"status\":\"PAID\"}" | kcat -b localhost:29092 -t payment.result -P -K:

```
