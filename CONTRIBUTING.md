# Contributing to Chaos Symphony

Thank you for your interest in contributing to Chaos Symphony! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Style Guide](#style-guide)

---

## Code of Conduct

Please be respectful and constructive in all interactions. We're building software, not fighting wars.

---

## Getting Started

### Prerequisites

- JDK 21+
- Maven 3.9+
- Docker & Docker Compose
- Node.js 20+ (for UI work)
- Git

### Fork and Clone

```bash
git clone https://github.com/YOUR_USERNAME/chaos-symphony.git
cd chaos-symphony
```

---

## Development Setup

### 1. Build the Project

```bash
mvn clean install -DskipTests
```

### 2. Start Infrastructure

```bash
cd deployment
docker-compose up -d postgres kafka zookeeper schema-registry
```

### 3. Run Tests

```bash
mvn verify
```

### 4. Start Services Locally

Each service can be run independently:

```bash
cd order-api
mvn spring-boot:run
```

Or use the full stack:

```bash
cd deployment
docker-compose up -d --build
```

---

## Making Changes

### Branch Naming

- `feature/` - New features (e.g., `feature/saga-visualization`)
- `fix/` - Bug fixes (e.g., `fix/dlq-retry-loop`)
- `refactor/` - Code refactoring (e.g., `refactor/extract-common-kafka-config`)
- `docs/` - Documentation changes (e.g., `docs/api-guide-update`)

### Commit Messages

Use conventional commits:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `docs`: Documentation
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**

```
feat(orchestrator): add saga state persistence

Implement SagaInstance entity for tracking saga progress across restarts.
Enables proper compensation even after service failures.

Closes #42
```

```
fix(payment-svc): prevent duplicate payment processing

Add idempotency check before processing PaymentRequested events.
```

---

## Testing

### Test Requirements

All PRs must maintain or improve test coverage:

- **Unit tests**: Required for all new business logic
- **Integration tests**: Required for Kafka listeners and database interactions
- **Contract tests**: Required for any changes to message formats

### Running Tests

```bash
# All tests
mvn verify

# Specific module
mvn -pl payment-svc test

# With coverage report
mvn verify jacoco:report
# View report: target/site/jacoco/index.html

# Contract tests only
./scripts/run-contract-tests.sh
```

### Test Naming Convention

```java
@Test
void methodName_givenCondition_shouldExpectedBehavior() {
    // Given
    // When  
    // Then
}
```

---

## Pull Request Process

### Before Submitting

1. **Rebase on main**: `git rebase main`
2. **Run all tests**: `mvn verify`
3. **Check formatting**: `mvn spotless:check`
4. **Update documentation** if needed

### PR Template

Your PR description should include:

```markdown
## What

Brief description of the change.

## Why

Context and motivation.

## How

Technical approach taken.

## Testing

How was this tested?

## Checklist

- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No breaking changes (or documented)
- [ ] Passes CI pipeline
```

### Review Process

1. At least one approval required
2. All CI checks must pass
3. No unresolved conversations
4. Squash merge preferred

---

## Style Guide

### Java

- **Formatter**: Google Java Format (enforced by Spotless)
- **Max line length**: 100 characters
- **Use Lombok** for boilerplate (but not `@Data` on JPA entities)

```java
// Good
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    
    public void processOrder(String orderId) {
        log.info("Processing order: {}", orderId);
        // ...
    }
}

// Avoid
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private OrderRepository orderRepository;
    
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
```

### Kafka Messages

- Use `EnvelopeHelper` for all messages
- Always include `orderId` and `eventId` in the envelope
- Use descriptive event types: `PaymentRequested`, not `PayReq`

### Error Handling

- Log at appropriate levels:
  - `ERROR`: Unrecoverable failures
  - `WARN`: Recoverable issues (DLQ, retries)
  - `INFO`: Significant business events
  - `DEBUG`: Detailed flow information

```java
try {
    processMessage(message);
} catch (TransientException e) {
    log.warn("Transient error processing message {}, will retry: {}", 
            message.getId(), e.getMessage());
    throw e; // Let retry mechanism handle it
} catch (Exception e) {
    log.error("Fatal error processing message {}: {}", 
            message.getId(), e.getMessage(), e);
    // Send to DLQ
}
```

---

## Architecture Decisions

Major architectural changes require an ADR (Architecture Decision Record). Add them to `ARCHITECTURE.md`:

```markdown
## ADR-XXXX: Title

**Status**: Proposed | Accepted | Deprecated

**Context**: Why is this decision needed?

**Decision**: What was decided?

**Consequences**: What are the trade-offs?
```

---

## Questions?

- Open an issue for bugs or feature requests
- Start a discussion for design questions
- Check existing issues before creating new ones

---

**Thank you for contributing to Chaos Symphony!** 🎵
