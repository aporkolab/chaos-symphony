# Payment Service

This service is responsible for handling payment processing for an order.

## Responsibilities

-   **Process Payments:** Listens for `payment.requested` events from the orchestrator.
-   **Simulate Payment Logic:** Simulates a payment attempt (in this demo, it succeeds or fails randomly).
-   **Publish Results:** Publishes a `payment.result` event indicating whether the charge was successful or not.
-   **Idempotency:** Uses an idempotency store to ensure that the same payment request is not processed more than once.

## Canary Mode

This service can be run in "canary" mode by activating the `canary` Spring profile. When in canary mode, it listens to the `payment.requested.canary` topic and uses a different consumer group (`payment-requested-canary`). This allows for testing a new version of the service on a small percentage of live traffic.

## Pact Contract Testing

This service acts as a "Provider" for the message contract defined by the `orchestrator` service.



- `PaymentSvcPactVerificationTest.java` - Verifies this service can handle payment request messages as defined by the orchestrator
- `PaymentResultContractTest.java` - Defines the contract for payment result messages this service produces

**Running Contract Tests:**

```bash
# From project root
./scripts/run-contract-tests.sh

# Or manually:
# 1. Generate consumer contracts
cd orchestrator && mvn test -Dtest="*Pact*Test"

# 2. Verify provider can handle contracts  
cd ../payment-svc && mvn test -Dtest="*PactVerificationTest"
```

The contract tests ensure that the message formats between orchestrator and payment-svc remain compatible across changes.
