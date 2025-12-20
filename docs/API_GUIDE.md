# API Usage and Postman Guide

This guide provides detailed instructions for interacting with the Chaos Symphony services using Postman or cURL. It's your primary reference for testing, debugging, and demonstrating the system's features.

## 📮 Using Postman for Automated Testing

For a streamlined experience, Postman collections and environments are included in the `docs/postman/` directory.

* **Collections:**
    * `ChaosSymphony.order_orchestrator.postman_collection.json`
    * `ChaosSymphony.dlq_admin.postman_collection.json`
    * `ChaosSvc.postman_collection.json`
* **Environments:**
    * `ChaosSymphony.services_environment.json` (contains base URLs for all services)

### How to Use

1.  **Import** the three collection files and the environment file into Postman.
2.  From the Environments tab, select **Chaos Symphony - Services** as your active environment.
3.  You can now use the pre-configured requests to interact with the services.

### Simulating Load

To simulate multiple orders, use the **Postman Runner** on the `Order & Orchestrator` collection. Select the `Start NEW order (random UUID)` request and set the number of iterations (e.g., 20-50) to generate load on the system.

---

## 👨‍💻 Using cURL from the Command Line

Here are the essential cURL commands for demonstrating the core functionalities of the system.

### `order-api` (Port `8080`)

This is the main entry point for starting the Saga workflow.

```bash
# Create an order using the full API (JSON body)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-123",
    "total": 99.99,
    "currency": "USD",
    "shippingAddress": "123 Main St"
  }'

# Create a high-value order (triggers fraud review)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-123",
    "total": 1500.00,
    "currency": "USD"
  }'

# Quick order creation for demos (query params)
curl -X POST "http://localhost:8080/api/orders/start?amount=42&customerId=customer-123"

# Quick high-value order (will be flagged for review)
curl -X POST "http://localhost:8080/api/orders/start?amount=1500&customerId=customer-456"

# List orders pending review
curl -s http://localhost:8080/api/orders/pending-review | jq .

# Approve an order after manual review
curl -X POST "http://localhost:8080/api/orders/{orderId}/approve"

# Reject an order with reason
curl -X POST "http://localhost:8080/api/orders/{orderId}/reject" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Suspected fraud - address mismatch"}'

# Get all orders (paginated)
curl -s "http://localhost:8080/api/orders?page=0&size=10" | jq .
```

### `chaos-svc` (Port `8088`)

This service allows you to define chaos rules and manage canary releases at runtime.

```bash
# Create a DELAY rule affecting all topics
curl -X POST http://localhost:8088/api/chaos/rules \
  -H "Content-Type: application/json" \
  -d '{
    "targetTopic": "all",
    "faultType": "DELAY",
    "probability": 0.3,
    "delayMs": 1200
  }'

# Get all active chaos rules
curl -s http://localhost:8088/api/chaos/rules | jq .

# Delete a specific chaos rule
curl -X DELETE "http://localhost:8088/api/chaos/rules/{ruleId}"

# Enable canary release (5% traffic)
curl -X POST http://localhost:8088/api/canary/config \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "percentage": 0.05
  }'

# Disable canary release
curl -X POST http://localhost:8088/api/canary/config \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": false,
    "percentage": 0.0
  }'
```

### `dlq-admin` (Port `8089`)

This service lets you inspect and manage Dead-Letter Topics (DLTs).

```bash
# List all topics that currently have messages in their DLT
curl -s http://localhost:8089/api/dlq/topics | jq .

# Peek at the first 5 messages in the inventory DLT
# Replace 'inventory.requested.DLT' with a topic from the list above
curl -s "http://localhost:8089/api/dlq/inventory.requested.DLT/peek?n=5" | jq .

# Replay all messages from a specific DLT to re-process them
curl -X POST http://localhost:8089/api/dlq/inventory.requested.DLT/replay
```

### `streams-analytics` (Port `8095`)

This service exposes real-time metrics calculated by Kafka Streams.

```bash
# Get the counts of different payment statuses
curl -s http://localhost:8095/api/metrics/paymentStatus | jq .

# Get SLO metrics for UI dashboard
curl -s http://localhost:8095/api/slo/metrics | jq .
```

