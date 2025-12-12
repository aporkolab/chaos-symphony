# Chaos Symphony

[![CI Build and Test](https://github.com/APorkolab/chaos-symphony/actions/workflows/ci.yml/badge.svg)](https://github.com/APorkolab/chaos-symphony/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-Streams-black.svg)](https://kafka.apache.org/)
[![Coverage](https://img.shields.io/badge/coverage-60%25+-brightgreen.svg)](https://github.com/APorkolab/chaos-symphony)
[![Security](https://img.shields.io/badge/security-OAuth2%2FJWT-blue.svg)](https://github.com/APorkolab/chaos-symphony)

> **Author:** Dr. Ádám Porkoláb  
> **Repository:** [github.com/APorkolab/chaos-symphony](https://github.com/APorkolab/chaos-symphony)

---
## 🎬 Demo Video

[![Chaos Symphony Demo](https://img.youtube.com/vi/XCwCOwdHuto/maxresdefault.jpg)](https://youtu.be/XCwCOwdHuto)

> 3-minute walkthrough: order processing, fraud detection, chaos engineering, and observability.
---

**Chaos Symphony** is a production-grade, event-driven microservices platform demonstrating how to build **resilient distributed systems**. It implements an order processing workflow with real business logic (fraud detection, manual review queues), orchestrated via the **Saga pattern**, with built-in **chaos engineering** capabilities.

> *A system's true strength is revealed not when it's running perfectly, but when it's gracefully handling failures.*

---

## 🎯 What Makes This Different

This is **not** a typical CRUD microservices demo. Here's what sets it apart:

| Most Demos | Chaos Symphony |
|------------|----------------|
| Empty services that just pass messages | **Real business logic**: fraud detection, velocity checks, manual review queues |
| "It works on my machine" | **Production patterns**: Outbox, Idempotency, DLQ with exponential backoff |
| Hope nothing breaks | **Chaos engineering built-in**: programmatic fault injection, automated GameDay experiments |
| Console.log debugging | **Full observability**: OpenTelemetry traces, Prometheus metrics, Grafana dashboards, SLO monitoring |
| "Deploy and pray" | **Operational runbook**: documented incident response procedures |

### Business Logic Highlights

```
Order Creation Flow:
├── Fraud Detection (rule-based scoring)
│   ├── High-value check (>$1000 → review)
│   ├── Velocity check (multiple orders → flag)
│   ├── Round number detection
│   └── Off-hours risk scoring
├── If flagged → PENDING_REVIEW (manual approval required)
├── If approved → Saga workflow begins
│   ├── Payment processing
│   ├── Inventory reservation
│   └── Shipping
└── Full compensation on any failure
```

---

## Table of Contents

- [What Makes This Different](#-what-makes-this-different)
- [Service Level Objectives](#service-level-objectives-slos)
- [Architecture Overview](#architecture-overview)
- [Key Patterns Implemented](#key-patterns-implemented)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Testing Strategy](#testing-strategy)
- [Security](#security)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Demo Script](#5-minute-demo-script)
- [Project Structure](#project-structure)
- [Contributing](#contributing)

---

## Service Level Objectives (SLOs)

| Service Level Indicator (SLI) | Objective (per day) |
| :--- | :--- |
| **End-to-End Latency** | `p95(order_processing_time) < 2000ms` |
| **Availability** | `successful_requests / total_requests >= 99.5%` |
| **Data Integrity** | `dlt_messages_total < 0.3% of total messages` |
| **Fraud Detection** | `flagged_orders / high_value_orders >= 95%` |

---

## Architecture Overview

### Core Principles

- **Event-Driven Architecture (EDA):** Asynchronous message passing via Apache Kafka. Services are decoupled, scalable, and use the Outbox pattern for data consistency.
- **Resilience by Design:** Idempotent Consumers, Dead-Letter Queues (DLQs), exponential backoff retries, and circuit breakers.
- **Deep Observability:** Prometheus metrics, OpenTelemetry distributed traces, structured JSON logging, and pre-configured Grafana dashboards.
- **Chaos Engineering as a First-Class Citizen:** Dedicated `chaos-svc` and `gameday-svc` for programmatic fault injection and automated experiments.

### System Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CHAOS SYMPHONY                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐    ┌───────────┐    ┌────────────┐    ┌──────────────┐         │
│  │   UI    │───▶│ order-api │───▶│ PostgreSQL │───▶│   Debezium   │         │
│  │ Angular │    └───────────┘    └────────────┘    └──────┬───────┘         │
│  └─────────┘                                              │                 │
│       │                                                   ▼                 │
│       │         ┌──────────────────────────────────────────────────┐        │
│       │         │                  Apache Kafka                    │        │
│       │         │  ┌─────────────┬─────────────┬─────────────────┐ │        │
│       │         │  │order.created│payment.req  │inventory.req    │ │        │
│       │         │  └─────────────┴─────────────┴─────────────────┘ │        │
│       │         └────────────────────────┬─────────────────────────┘        │
│       │                                  │                                  │
│       │    ┌─────────────────────────────┼─────────────────────────────┐    │
│       │    │                             ▼                             │    │
│       │    │  ┌──────────────┐    ┌─────────────┐    ┌─────────────┐   │    │
│       │    │  │ orchestrator │───▶│ payment-svc │───▶│inventory-svc│   │    │
│       │    │  └──────────────┘    └─────────────┘    └─────────────┘   │    │
│       │    │         │                                      │          │    │
│       │    │         └──────────────────┬───────────────────┘          │    │
│       │    │                            ▼                              │    │
│       │    │                    ┌──────────────┐                       │    │
│       │    │                    │ shipping-svc │                       │    │
│       │    │                    └──────────────┘                       │    │
│       │    └──────────────────── SAGA PATTERN ─────────────────────────┘    │
│       │                                                                     │
│       │    ┌──────────────────── SUPPORTING SERVICES ─────────────────┐     │
│       └───▶│  ┌───────────┐  ┌───────────┐  ┌─────────────────────┐   │     │
│            │  │ chaos-svc │  │ dlq-admin │  │ streams-analytics   │   │     │
│            │  └───────────┘  └───────────┘  └─────────────────────┘   │     │
│            │        │                                    │            │     │
│            │        ▼                                    ▼            │     │
│            │  ┌───────────┐                     ┌──────────────┐      │     │
│            │  │gameday-svc│                     │  Prometheus  │      │     │
│            │  └───────────┘                     └──────────────┘      │     │
│            └──────────────────────────────────────────────────────────┘     │
│                                                                             │
│    ┌──────────────────────── OBSERVABILITY ───────────────────────────┐     │
│    │  ┌────────────────┐    ┌────────────┐    ┌─────────────────┐     │     │
│    │  │ OTel Collector │───▶│ Prometheus │───▶│     Grafana     │     │     │
│    │  └────────────────┘    └────────────┘    └─────────────────┘     │     │
│    └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Patterns Implemented

| Pattern | Implementation | Business Value |
| :--- | :--- | :--- |
| **Saga Pattern** | Orchestrated workflow across `orchestrator`, `payment-svc`, `inventory-svc`, and `shipping-svc` via Kafka messages. | Ensures long-running processes complete or safely compensate without distributed locks. |
| **Outbox Pattern** | `order-api` writes events to its database atomically; Debezium CDC publishes to Kafka. | Guarantees at-least-once delivery, prevents dual-write problems. |
| **Idempotent Consumer** | All services track processed message IDs in their database. | Prevents duplicate processing (e.g., double-charging customers). |
| **Dead-Letter Queue (DLQ)** | Spring Kafka `@RetryableTopic` with exponential backoff. Unrecoverable messages go to `*.dlt` topics. | Isolates poison-pill messages without halting the system. |
| **Windowed SLO Monitoring** | `streams-analytics` uses Kafka Streams for rolling window metrics. | Real-time actionable health metrics against SLOs. |
| **Automated GameDay** | `gameday-svc` triggers chaos experiments with SLO monitoring. | Continuous resilience validation. |
| **Canary Releases** | Application-level traffic splitting with dedicated canary topics. | Safe progressive rollouts without service mesh. |

---

## Tech Stack

| Layer | Technologies |
|-------|-------------|
| **Languages** | Java 21, TypeScript |
| **Frameworks** | Spring Boot 3.5, Angular 17 |
| **Messaging** | Apache Kafka, Confluent Schema Registry, Debezium CDC |
| **Database** | PostgreSQL 16 |
| **Security** | Spring Security OAuth2 Resource Server, JWT, Keycloak/Auth0 compatible |
| **Observability** | OpenTelemetry, Prometheus, Grafana, Micrometer |
| **Testing** | JUnit 5, Testcontainers, Pact (Contract Testing), Awaitility |
| **Infrastructure** | Docker, Kubernetes, Kustomize |
| **CI/CD** | GitHub Actions, OWASP Dependency-Check, Trivy Security Scanner, Codecov |

---

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Maven 3.9+ & JDK 21
- Node.js 20+ (for UI development)

### 1. Build the Project

```bash
mvn -B clean verify
```

### 2. Start the Stack

```bash
cd deployment
docker-compose up -d --build
```

### 3. Access the System

| Service | URL | Credentials |
|---------|-----|-------------|
| **Angular UI** | http://localhost:4200 | — |
| **Grafana** | http://localhost:3000 | admin/admin |
| **Prometheus** | http://localhost:9090 | — |
| **Kafka UI (Kafdrop)** | http://localhost:9000 | — |

---

## Testing Strategy

The project implements a comprehensive testing pyramid:

### Unit Tests
- **JUnit 5** with `@Nested` test classes for organization
- **Mockito** for dependency isolation
- **AssertJ** for fluent assertions

### Integration Tests
- **Testcontainers** for production-like testing with real Kafka and PostgreSQL
- **Spring Boot Test** with `@SpringBootTest` for full context loading
- **Embedded Kafka** for lightweight Kafka integration tests

### Contract Tests
- **Pact** for consumer-driven contract testing between services
- Ensures API compatibility between producer and consumer services
- Contract files stored in `/contracts/` directory

### E2E Tests
- Full Saga workflow tests using Testcontainers
- Tests happy path, compensation flows, and edge cases
- Validates idempotency and concurrency handling

```bash
# Run all tests
mvn clean verify

# Run only unit tests
mvn test

# Run integration tests (requires Docker)
mvn verify -DskipITs=false
```

---

## Security

### Authentication & Authorization

The project implements **OAuth2 Resource Server** with JWT validation:

```yaml
# Supported Identity Providers:
- Keycloak (realm_access.roles claim)
- Auth0 (permissions claim)
- Any OIDC-compliant provider
```

### Security Features

| Feature | Implementation |
|---------|----------------|
| **Authentication** | JWT Bearer tokens via Spring Security OAuth2 Resource Server |
| **Authorization** | Role-based access control (RBAC) with `@PreAuthorize` |
| **Development Mode** | Configurable `dev` profile disables auth for local testing |
| **Rate Limiting** | Actuator endpoints protected, health checks public |

### Configuration

```yaml
# Production (environment variables)
OAUTH2_ISSUER_URI=https://your-keycloak.com/realms/chaos-symphony
SECURITY_MODE=jwt

# Development
SECURITY_MODE=dev  # Disables authentication
```

### Secure Endpoints

```
✅ Public:      /actuator/health/*, /v3/api-docs/*, /swagger-ui/*
🔒 Protected:   /api/dlq/* (requires ADMIN or OPERATOR role)
🔒 Protected:   /api/orders/* (requires authenticated user)
```

---

## Kubernetes Deployment

The project includes production-grade Kubernetes manifests in `/kubernetes/` with:

- Resource limits and requests
- Liveness and readiness probes
- PodDisruptionBudgets
- Security contexts
- Prometheus annotations

### Deploy

```bash
# Build and push images to your registry
docker build -t your-registry/payment-svc:latest payment-svc/
docker push your-registry/payment-svc:latest
# Repeat for all services...

# Update image names in deployment.yaml files, then:
kubectl apply -k kubernetes/
```

---

## 5-Minute Demo Script

1. **Healthy State:** Open UI → SLO view. All metrics green.

2. **Create Order:** Orders view → "Create New Order". Triggers SAGA workflow.

3. **Inject Chaos:** Chaos view → Enable Delay (1200ms), Duplicate, Mutate. Create orders. Watch SLO view turn red.

4. **Drill-Down:** DLQ view → Inspect failed messages. Disable chaos → "Replay All".

5. **Canary Release:** Enable Canary toggle. View Grafana canary comparison dashboard.

6. **Time-Travel:** Orders view → "Replay 5m" to re-process recent events.

7. **Automated GameDay:** Check GitHub Actions → GameDay workflow → Download report artifact.

---

## Project Structure

```
chaos-symphony/
├── chaos-svc/          # Chaos rule management service
├── common-messaging/   # Shared messaging utilities, Avro schemas
├── dlq-admin/          # Dead-letter queue administration
├── gameday-svc/        # Automated chaos experiments
├── inventory-svc/      # Inventory reservation service
├── orchestrator/       # Saga orchestration with enum-based compensation
├── order-api/          # Order API with fraud detection & manual review
├── payment-svc/        # Payment processing with canary support
├── shipping-svc/       # Shipping fulfillment
├── streams-analytics/  # Kafka Streams SLO analytics
├── ui/dashboard/       # Angular control panel
├── deployment/         # Docker Compose, Prometheus, Grafana
├── kubernetes/         # K8s manifests with Kustomize
├── docs/               # API guides, Postman collections
└── scripts/            # Utility scripts
```

---

## API Examples

### Create Order (triggers fraud check)
```bash
# Low-value order → processes immediately
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "cust-123", "total": 99.99, "currency": "USD"}'

# Response: {"orderId": "...", "status": "NEW", "reviewReason": null}
```

```bash
# High-value order → flagged for review
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "cust-123", "total": 1500.00, "currency": "USD"}'

# Response: {"orderId": "...", "status": "PENDING_REVIEW", "reviewReason": "High value order: $1500.00 (score +35.0)"}
```

### Approve/Reject Flagged Order
```bash
# Approve after review
curl -X POST http://localhost:8080/api/orders/{orderId}/approve

# Reject with reason
curl -X POST http://localhost:8080/api/orders/{orderId}/reject \
  -H "Content-Type: application/json" \
  -d '{"reason": "Fraud confirmed - stolen card"}'
```

### List Pending Reviews
```bash
curl http://localhost:8080/api/orders/pending-review
```

---

## Anti-CRUD Checklist

### Business Logic
- [x] **Fraud Detection** — Rule-based scoring with velocity checks, value thresholds, round number detection
- [x] **Manual Review Queue** — High-risk orders held for human approval
- [x] **Order State Machine** — Proper lifecycle with documented transitions

### Distributed Systems Patterns
- [x] **SAGA Pattern** — Orchestrated order workflow with full compensation
- [x] **Saga State Persistence** — Survives restarts, enum-based retry strategy
- [x] **Outbox Pattern** — Debezium CDC for exactly-once event publishing
- [x] **Idempotent Consumers** — All services track processed message IDs
- [x] **DLQ Policy** — Exponential backoff via `@RetryableTopic`

### Resilience & Chaos
- [x] **Chaos Engineering** — Programmatic fault injection (delay, drop, corrupt, duplicate)
- [x] **Automated GameDay** — GitHub Actions chaos workflow with SLO validation
- [x] **Graceful Shutdown** — Proper Kafka consumer drain

### Observability
- [x] **Distributed Tracing** — OpenTelemetry integration
- [x] **Metrics** — Prometheus with custom business metrics (fraud scores, review rates)
- [x] **Dashboards** — Pre-configured Grafana with SLO panels
- [x] **Operational Runbook** — Documented incident response

### Quality & Security
- [x] **Test Coverage** — JaCoCo 60%+ enforced, Testcontainers for integration
- [x] **Contract Tests** — Pact consumer/provider verification
- [x] **OAuth2/JWT Security** — Production-grade authentication
- [x] **Security Scanning** — Trivy in CI pipeline

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <b>Built with ❤️ by Dr. Porkoláb Ádám</b>
</p>
