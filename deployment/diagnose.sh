#!/bin/bash



echo "=========================================="
echo "CHAOS SYMPHONY DIAGNOSTICS"
echo "=========================================="
echo ""


echo "1. SERVICE STATUS"
echo "-----------------"
docker-compose ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null || docker-compose ps
echo ""


echo "2. DEBEZIUM CONNECTOR STATUS"
echo "----------------------------"
CONNECTOR_STATUS=$(curl -s http://localhost:8093/connectors/orders-connector/status 2>/dev/null)
if [ -z "$CONNECTOR_STATUS" ]; then
    echo "ERROR: Cannot reach Kafka Connect at localhost:8093"
    echo "       Make sure 'connect' service is running"
else
    echo "$CONNECTOR_STATUS" | python3 -m json.tool 2>/dev/null || echo "$CONNECTOR_STATUS"
fi
echo ""


echo "3. REGISTERED CONNECTORS"
echo "------------------------"
curl -s http://localhost:8093/connectors 2>/dev/null || echo "Cannot reach Kafka Connect"
echo ""


echo "4. KAFKA TOPICS"
echo "---------------"
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E "order|payment|inventory|shipping|debezium" || echo "Cannot list topics"
echo ""


echo "5. ORDER.CREATED TOPIC (last 3 messages)"
echo "-----------------------------------------"
docker-compose exec -T kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic order.created \
    --from-beginning \
    --max-messages 3 \
    --timeout-ms 5000 2>/dev/null || echo "No messages or topic doesn't exist"
echo ""


echo "6. POSTGRES WAL_LEVEL"
echo "---------------------"
docker-compose exec -T postgres psql -U app -d orders -c "SHOW wal_level;" 2>/dev/null || echo "Cannot connect to postgres"
echo ""


echo "7. ORDER_OUTBOX TABLE (last 5 entries)"
echo "--------------------------------------"
docker-compose exec -T postgres psql -U app -d orders \
    -c "SELECT id, aggregate_id, type, created_at FROM order_outbox ORDER BY created_at DESC LIMIT 5;" 2>/dev/null || echo "Cannot query outbox"
echo ""


echo "8. ORDERS TABLE (last 5 orders)"
echo "--------------------------------"
docker-compose exec -T postgres psql -U app -d orders \
    -c "SELECT id, status, created_at FROM orders ORDER BY created_at DESC LIMIT 5;" 2>/dev/null || echo "Cannot query orders"
echo ""


echo "9. ORCHESTRATOR LOGS (last 20 lines)"
echo "-------------------------------------"
docker-compose logs --tail=20 orchestrator 2>/dev/null || echo "Cannot get orchestrator logs"
echo ""


echo "10. DEBEZIUM-INIT LOGS"
echo "----------------------"
docker-compose logs debezium-init 2>/dev/null || echo "debezium-init service not found or never ran"
echo ""

echo "=========================================="
echo "DIAGNOSIS COMPLETE"
echo "=========================================="
echo ""
echo "COMMON ISSUES:"
echo "- If connector status is 'FAILED' or missing: Debezium not working"
echo "- If wal_level is not 'logical': Postgres needs restart with wal_level=logical"
echo "- If order_outbox is empty: Orders aren't creating outbox entries"
echo "- If order.created topic has no messages: Debezium isn't publishing"
echo "- If orchestrator shows no 'order.created' consumption: Check consumer group"
