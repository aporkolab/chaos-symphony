# Debezium Connector Configuration

This document provides the detailed configuration and management commands for the Debezium PostgreSQL connector used in Chaos Symphony. The connector is configured to use the Outbox Event Router pattern.

## Connector Configuration

This is the JSON payload used to configure the connector via the Kafka Connect REST API. It tells Debezium to monitor the `public.order_outbox` table and transform its rows into business-level Kafka events.

**Key Configuration Parameters:**

* `transforms=outbox`: Enables the Event Router transformation.
* `transforms.outbox.route.topic.replacement`: Dynamically sets the destination Kafka topic based on a field in the database row (in our case, the `type` column).
* `transforms.outbox.table.field.event.*`: Maps columns from the `order_outbox` table (`id`, `aggregate_id`, `type`, `payload`) to the final Kafka message's key, payload, and headers.

### Create or Update Connector Command

Use this `cURL` command to register and configure the connector with Kafka Connect.

```bash
curl -i -X PUT http://localhost:8083/connectors/orders-connector/config \
  -H "Content-Type: application/json" \
  -d '{
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "debezium",
    "database.password": "dbz",
    "database.dbname": "orders",
    "topic.prefix": "ordersdb",
    "slot.name": "orders_slot",
    "plugin.name": "pgoutput",
    "publication.name": "dbz_publication",
    "publication.autocreate.mode": "disabled",
    "table.include.list": "public.order_outbox",
    "tombstones.on.delete": "false",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "type",
    "transforms.outbox.table.field.payload": "payload",
    "transforms.outbox.table.field.payload.id.field": "id",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }'

```

## Managing the Connector

Use these endpoints to manage the connector's lifecycle.

Bash

```
# Check connector status
curl -s http://localhost:8083/connectors/orders-connector/status | jq .

# Restart the connector (e.g., after a configuration change)
curl -s -X POST http://localhost:8083/connectors/orders-connector/restart

# Pause the connector
curl -s -X PUT http://localhost:8083/connectors/orders-connector/pause

# Resume the connector
curl -s -X PUT http://localhost:8083/connectors/orders-connector/resume

```