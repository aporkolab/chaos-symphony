#!/bin/bash
# Bootstrap CDC connector for Chaos Symphony
# This script registers the Debezium PostgreSQL connector with Kafka Connect

set -e

CONNECT_URL="http://localhost:8093"
CONNECTOR_CONFIG_FILE="deployment/debezium-orders.json"

echo "Bootstrapping Debezium CDC connector..."

# Wait for Kafka Connect to be fully ready
echo "Waiting for Kafka Connect to be ready..."
timeout 120s bash -c 'until curl -s $0/connectors > /dev/null; do echo -n "."; sleep 2; done' $CONNECT_URL
echo -e "\nKafka Connect is ready!"

# Check if connector already exists
if curl -s "$CONNECT_URL/connectors/orders-connector" | grep -q "orders-connector"; then
    echo "Connector 'orders-connector' already exists. Deleting it first..."
    curl -X DELETE "$CONNECT_URL/connectors/orders-connector"
    sleep 2
fi

# Register the connector
echo "Registering Debezium orders connector..."
curl -X POST \
    -H "Content-Type: application/json" \
    -d @$CONNECTOR_CONFIG_FILE \
    "$CONNECT_URL/connectors"

echo -e "\nConnector registered successfully!"

# Verify the connector status
sleep 3
echo "Connector status:"
curl -s "$CONNECT_URL/connectors/orders-connector/status" | jq '.'

echo "CDC bootstrap completed!"
