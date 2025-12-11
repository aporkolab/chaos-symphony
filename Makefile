# ==============================================================================
# Makefile for Chaos Symphony Project
# ==============================================================================

# Variables
DOCKER_COMPOSE_FILE = deployment/docker-compose.yml
MAVEN_SERVICES = -pl order-api,orchestrator,payment-svc,inventory-svc,shipping-svc,streams-analytics,dlq-admin,chaos-svc

.PHONY: help up down run-backend run-ui test clean

help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  up             - Start all infrastructure containers (Kafka, Postgres, etc.)"
	@echo "  down           - Stop all infrastructure containers"
	@echo "  run-backend    - Run all backend Spring Boot services locally"
	@echo "  run-ui         - Run the Angular UI development server"
	@echo "  test           - Run all backend Maven tests"
	@echo "  clean          - Clean all Maven projects"

up:
	@echo "Starting infrastructure..."
	@docker compose -f $(DOCKER_COMPOSE_FILE) up --build -d

down:
	@echo "Stopping infrastructure..."
	@docker compose -f $(DOCKER_COMPOSE_FILE) down

run-backend:
	@echo "Running all backend services..."
	@mvn $(MAVEN_SERVICES) spring-boot:run

run-ui:
	@echo "Starting Angular UI..."
	@cd ui/dashboard && npm start

test:
	@echo "Running backend tests..."
	@mvn -U -B clean verify

clean:
	@echo "Cleaning Maven projects..."
	@mvn clean
