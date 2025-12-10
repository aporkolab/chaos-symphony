#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
(cd deployment && docker compose up -d)
mvn -q -pl "payment-svc,inventory-svc,shipping-svc,orchestrator,dlq-admin,streams-analytics,order-api" spring-boot:run
