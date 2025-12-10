#!/bin/sh
set -e

CONNECT_URL="http://connect:8083"
CONNECTOR_NAME="orders-connector"
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "Waiting for Kafka Connect to be ready..."

i=1
while [ $i -le $MAX_RETRIES ]; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$CONNECT_URL/connectors" || echo "000")
    
    if [ "$HTTP_CODE" = "200" ]; then
        echo "Kafka Connect is ready!"
        break
    fi
    
    if [ $i -eq $MAX_RETRIES ]; then
        echo "ERROR: Kafka Connect not ready after $MAX_RETRIES attempts"
        exit 1
    fi
    
    echo "Attempt $i/$MAX_RETRIES - Kafka Connect not ready (HTTP $HTTP_CODE), waiting ${RETRY_INTERVAL}s..."
    sleep $RETRY_INTERVAL
    i=$((i + 1))
done


EXISTING=$(curl -s -o /dev/null -w "%{http_code}" "$CONNECT_URL/connectors/$CONNECTOR_NAME")

if [ "$EXISTING" = "200" ]; then
    echo "Connector '$CONNECTOR_NAME' already exists. Checking status..."
    STATUS_JSON=$(curl -s "$CONNECT_URL/connectors/$CONNECTOR_NAME/status")
    TASK_STATE=$(echo "$STATUS_JSON" | grep -o '"state":"[^"]*"' | tail -1 | sed 's/"state":"//;s/"//')
    
    if [ "$TASK_STATE" = "FAILED" ]; then
        echo "Connector task is FAILED. Deleting and recreating..."
        curl -s -X DELETE "$CONNECT_URL/connectors/$CONNECTOR_NAME"
        sleep 3
    else
        echo "Connector task status: $TASK_STATE. No action needed."
        exit 0
    fi
fi

echo "Registering Debezium connector '$CONNECTOR_NAME'..."

RESULT=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d @/config/debezium-orders.json \
    "$CONNECT_URL/connectors")

echo "Registration result: $RESULT"
echo ""
echo "Waiting for connector to start..."
sleep 5


STATUS_JSON=$(curl -s "$CONNECT_URL/connectors/$CONNECTOR_NAME/status")
TASK_STATE=$(echo "$STATUS_JSON" | grep -o '"state":"[^"]*"' | tail -1 | sed 's/"state":"//;s/"//')

echo "Task state: $TASK_STATE"

if [ "$TASK_STATE" = "RUNNING" ]; then
    echo "SUCCESS: Debezium connector is running!"
    exit 0
elif [ "$TASK_STATE" = "FAILED" ]; then
    echo "ERROR: Connector task failed!"
    echo "$STATUS_JSON"
    exit 1
else
    echo "Connector task state is '$TASK_STATE'."
    exit 0
fi
