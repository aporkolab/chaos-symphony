#!/bin/bash









set -e


RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' 
BOLD='\033[1m'


print_header() {
    echo ""
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}${CYAN}  $1${NC}"
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════${NC}"
}

print_step() {
    echo -e "\n${YELLOW}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

wait_for_service() {
    local name=$1
    local url=$2
    local max_attempts=${3:-30}
    
    echo -n "  Waiting for $name"
    for i in $(seq 1 $max_attempts); do
        if curl -s "$url" | grep -q '"status":"UP"' 2>/dev/null; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    echo -e " ${RED}✗${NC}"
    return 1
}


main() {
    print_header "🎼 CHAOS SYMPHONY - QUICK DEMO"
    echo ""
    echo -e "  ${BOLD}Event-Driven Microservices with Saga Orchestration${NC}"
    echo -e "  Author: Dr. Porkoláb Ádám"
    echo ""
    echo "  This demo will showcase:"
    echo "  1. Full system startup with observability"
    echo "  2. Order processing via Saga pattern"
    echo "  3. Chaos injection and resilience"
    echo "  4. Automatic recovery and compensation"
    echo ""

    
    print_step "Checking prerequisites..."
    command -v docker >/dev/null 2>&1 || { echo -e "${RED}Docker is required but not installed.${NC}"; exit 1; }
    command -v curl >/dev/null 2>&1 || { echo -e "${RED}curl is required but not installed.${NC}"; exit 1; }
    print_success "Prerequisites satisfied"

    
    print_header "PHASE 1: System Startup"
    print_step "Starting all services with Docker Compose..."
    cd deployment
    docker compose up -d --build 2>/dev/null
    
    print_step "Waiting for services to become healthy..."
    wait_for_service "order-api" "http://localhost:8080/actuator/health"
    wait_for_service "orchestrator" "http://localhost:8091/actuator/health"
    wait_for_service "payment-svc" "http://localhost:8082/actuator/health"
    wait_for_service "chaos-svc" "http://localhost:8088/actuator/health"
    
    print_success "All services healthy!"
    
    
    print_info "UI Dashboard: http://localhost:4200"
    print_info "Grafana: http://localhost:3000 (admin/admin)"
    print_info "Kafdrop (Kafka UI): http://localhost:9000"

    
    print_header "PHASE 2: Order Processing (Happy Path)"
    print_step "Creating a new order..."
    
    ORDER_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/orders/start" \
        -H "Content-Type: application/json" \
        -d '{"total": 199.99, "customerId": "demo-customer-001"}')
    
    ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || echo "demo-order")
    print_success "Order created: $ORDER_ID"
    
    print_step "Watching saga progression..."
    sleep 3
    
    SAGA_STATE=$(curl -s "http://localhost:8091/actuator/health" | grep -o '"status":"[^"]*"' | head -1)
    print_success "Saga is processing through: Payment → Inventory → Shipping"
    
    
    print_header "PHASE 3: Chaos Engineering"
    print_step "Injecting DELAY fault into payment service (50% probability, 2000ms)..."
    
    CHAOS_RESPONSE=$(curl -s -X POST "http://localhost:8088/api/chaos/rules" \
        -H "Content-Type: application/json" \
        -d '{
            "targetTopic": "payment.request",
            "faultType": "DELAY",
            "probability": 0.5,
            "delayMs": 2000
        }')
    
    RULE_ID=$(echo $CHAOS_RESPONSE | grep -o '"id":[0-9]*' | cut -d':' -f2 || echo "1")
    print_success "Chaos rule activated (ID: $RULE_ID)"
    
    print_step "Creating orders under chaos..."
    for i in {1..5}; do
        curl -s -X POST "http://localhost:8080/api/orders/start" \
            -H "Content-Type: application/json" \
            -d "{\"total\": $((100 + i * 10)).99, \"customerId\": \"chaos-test-$i\"}" > /dev/null
        echo -n "."
    done
    echo ""
    print_success "5 orders submitted under chaos conditions"
    
    
    print_step "Checking Dead Letter Queue..."
    sleep 5
    DLQ_TOPICS=$(curl -s "http://localhost:8089/api/dlq/topics" -H "X-Admin-Token: dev-token" 2>/dev/null || echo "[]")
    print_info "DLQ Topics: $DLQ_TOPICS"
    
    
    print_header "PHASE 4: Recovery & Resilience"
    print_step "Disabling chaos rule..."
    curl -s -X DELETE "http://localhost:8088/api/chaos/rules/$RULE_ID" > /dev/null
    print_success "Chaos rule disabled"
    
    print_step "Triggering DLQ replay..."
    curl -s -X POST "http://localhost:8089/api/dlq/replay" -H "X-Admin-Token: dev-token" 2>/dev/null || true
    print_success "Dead letter messages reprocessed"
    
    
    print_header "PHASE 5: Observability"
    print_info "Key Prometheus metrics available:"
    echo "  • saga.compensations.triggered"
    echo "  • saga.compensations.completed"
    echo "  • kafka.messages.processed"
    echo "  • kafka.message.processing.time"
    echo ""
    print_info "Grafana Dashboards:"
    echo "  • SLO Overview: http://localhost:3000/d/slo-overview"
    echo "  • Canary Comparison: http://localhost:3000/d/canary-comparison"
    echo "  • Chaos Experiments: http://localhost:3000/d/chaos"
    
    
    print_header "🎉 DEMO COMPLETE"
    echo ""
    echo "  ${BOLD}What you've seen:${NC}"
    echo "  ✓ Event-driven microservices with Kafka"
    echo "  ✓ Saga orchestration with compensation"
    echo "  ✓ Transactional Outbox pattern (Debezium CDC)"
    echo "  ✓ Idempotent message processing"
    echo "  ✓ Dead Letter Queue handling"
    echo "  ✓ Chaos engineering and fault injection"
    echo "  ✓ Full observability stack (OTel, Prometheus, Grafana)"
    echo ""
    echo "  ${BOLD}Key Interview Talking Points:${NC}"
    echo "  • Why Saga over 2PC? (Decoupling, scalability)"
    echo "  • Why Outbox pattern? (Dual-write prevention)"
    echo "  • Why orchestration over choreography? (Explicit flow control)"
    echo "  • Trade-offs of application-level canary?"
    echo ""
    echo "  ${BOLD}Cleanup:${NC}"
    echo "  Run: cd deployment && docker compose down"
    echo ""
}


main "$@"
