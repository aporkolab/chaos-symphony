# Chaos Symphony - Runbook

> **Author:** Dr. Porkoláb Ádám  
> **Last Updated:** 2025.12.09

This document provides step-by-step instructions for handling common operational issues in the Chaos Symphony system.

---

## Table of Contents

- [Chaos Symphony - Runbook](#chaos-symphony---runbook)
  - [Table of Contents](#table-of-contents)
    - [Scenario 1: DLQ Count is Rising Rapidly](#scenario-1-dlq-count-is-rising-rapidly)
    - [Scenario 2: p95 Latency Exceeds SLO (\> 2 seconds)](#scenario-2-p95-latency-exceeds-slo--2-seconds)
    - [Scenario 3: Schema Incompatibility Error](#scenario-3-schema-incompatibility-error)
    - [Scenario 4: Service Not Starting](#scenario-4-service-not-starting)
    - [Scenario 5: Kafka Consumer Lag Growing](#scenario-5-kafka-consumer-lag-growing)
    - [Scenario 6: Database Connection Issues](#scenario-6-database-connection-issues)
  - [Emergency Contacts](#emergency-contacts)
  - [Quick Reference](#quick-reference)

---

### Scenario 1: DLQ Count is Rising Rapidly

**Symptom:** The "DLT Count" panel in Grafana shows a sustained increase in messages. The `dlt_messages_total` metric is growing.

**Triage & Resolution:**

1.  **Assess the Impact:**

    - Navigate to the **DLQ** view in the application UI (`http://localhost:4200/dlq`).
    - Select the topic with the rising message count to inspect the failed messages.
    - Examine the headers and payload of a few messages. Look for a common root cause in the `x-exception-message` header (e.g., `ConnectException`, `NullPointerException`, a specific business error).

2.  **Attempt Initial Recovery:**

    - If the error seems transient (e.g., a temporary network issue), use the **"Retry All"** button in the DLQ UI for the affected topic.
    - Monitor the DLQ count in Grafana. If the messages are processed successfully and the count drops, the issue was likely transient.

3.  **Isolate the Fault (If Retry Fails):**
    - If messages fail again and land back in the DLQ, there is likely a persistent bug or misconfiguration.
    - **If a Canary deployment is active, immediately turn it off** via the Chaos Panel in the UI to halt the impact on the new version.
    - Notify the development team responsible for the failing service (e.g., `payment-svc` if messages are in `payment.requested.DLT`), providing them with sample message payloads and exception details from the DLQ UI.

---

### Scenario 2: p95 Latency Exceeds SLO (> 2 seconds)

**Symptom:** The "Order E2E p95 Latency" panel in Grafana is consistently above the 2000ms SLO threshold. The `orders_slo_burn_rate` is greater than 1.

**Triage & Resolution:**

1.  **Stop the Bleeding:**

    - Navigate to the **Chaos Panel** in the UI (`http://localhost:4200/chaos`).
    - **Immediately disable all active chaos experiments** (especially `DELAY`). This is the most likely cause of artificial latency.
    - Observe the p95 latency graph for the next 2-3 minutes. If it returns to normal, the chaos experiment was the cause.

2.  **Investigate Real Performance Issues (If Chaos Was Off):**

    - If latency remains high, check the **Kafka Consumer Lag** panel in Grafana. A high and rising lag on a particular topic indicates that the consumer service for that topic is overwhelmed or stuck.
    - Check the logs of the struggling service (e.g., `docker-compose logs -f payment-svc`) for errors, exceptions, or long-running query warnings.

3.  **Scale to Mitigate:**
    - As a temporary mitigation, you can scale the number of consumer instances for the affected service.
    - Example: `docker-compose up -d --scale payment-svc=3`
    - Monitor the consumer lag and p95 latency. If scaling helps, it indicates a throughput issue that the development team needs to investigate further.

---

### Scenario 3: Schema Incompatibility Error

**Symptom:** Messages are landing in the DLQ with a `JsonParseException` or a similar deserialization error. Logs for a consumer service show `Could not deserialize ...` errors.

**Triage & Resolution:**

1.  **Identify the Bad Schema:**

    - This typically happens after a deployment that introduced a breaking change in an event payload.
    - Confirm with the producing team which service was deployed recently and what changes were made to the event structure.

2.  **Isolate and Revert:**

    - **Do not retry the messages from the DLQ.** They will only fail again.
    - The first priority is to **revert the change in the producing service** that introduced the incompatible schema. This could be a code revert or a feature flag toggle.
    - Once the producer is fixed and deploying, new messages should be processed correctly.

3.  **Handle Failed Messages (Post-Fix):**
    - This requires manual intervention or a dedicated script.
    - **Option A (Replay with Transformation):** If possible, create a script that reads from the DLQ, transforms the old message payload to the new schema, and re-publishes it to the original topic.
    - **Option B (Time-Travel Replay):** If the business logic allows, and the volume of failed data is acceptable to lose/re-process, you could use a "Replay" feature (as described in the spec's point #6) to reset the consumer group's offset to a time before the bad deployment, effectively ignoring the poison pill messages.
    - **Option C (Manual Correction):** For critical, low-volume data, export the failed messages from the DLQ UI, manually correct the data in the database or via an admin API, and mark the DLQ messages as "purged".

---

### Scenario 4: Service Not Starting

**Symptom:** A service container keeps restarting or fails to start. `docker-compose ps` shows the service in `Restarting` or `Exit` state.

**Triage & Resolution:**

1.  **Check Logs:**

    ```bash
    docker-compose logs --tail=100 <service-name>
    ```

    - Look for `Application failed to start`, `Bean creation exception`, or `Port already in use` errors.

2.  **Common Causes:**

    - **Port conflict:** Another process is using the required port. Stop the conflicting process or change the port in `application.yml`.
    - **Missing environment variables:** Check if all required env vars are set in `docker-compose.yml`.
    - **Database not ready:** If the service depends on PostgreSQL, ensure the DB is healthy before the service starts (check `depends_on` with `condition: service_healthy`).
    - **Kafka not ready:** Services may fail if Kafka isn't accepting connections yet. Add appropriate health checks or increase `initialDelaySeconds` in K8s probes.

3.  **Rebuild if Necessary:**
    ```bash
    docker-compose build --no-cache <service-name>
    docker-compose up -d <service-name>
    ```

---

### Scenario 5: Kafka Consumer Lag Growing

**Symptom:** The Kafka consumer lag metric is growing continuously. Messages are not being processed fast enough.

**Triage & Resolution:**

1.  **Identify the Bottleneck:**

    - Check Kafdrop at `http://localhost:9000` to see which consumer groups have lag.
    - Look at the specific topic and partition with the highest lag.

2.  **Check Consumer Health:**

    - Verify the consumer service is running: `docker-compose ps`
    - Check for errors in consumer logs: `docker-compose logs -f <service-name>`
    - Look for `Consumer has been removed from the group` messages indicating rebalancing issues.

3.  **Scale Consumers:**

    ```bash
    # Scale horizontally
    docker-compose up -d --scale payment-svc=3
    ```

    - Note: Number of consumers cannot exceed number of partitions for a topic.

4.  **Check Processing Time:**
    - If individual message processing is slow, look at the `processing.time.ms` metric in Prometheus.
    - Investigate slow database queries or external API calls.

---

### Scenario 6: Database Connection Issues

**Symptom:** Services log `Connection refused` or `Too many connections` errors for PostgreSQL.

**Triage & Resolution:**

1.  **Verify PostgreSQL is Running:**

    ```bash
    docker-compose ps postgres
    docker-compose logs postgres
    ```

2.  **Check Connection Pool:**

    - Default HikariCP pool size is 10. If you have many service instances, you may exhaust connections.
    - Increase `spring.datasource.hikari.maximum-pool-size` if needed (but consider DB limits).

3.  **Check PostgreSQL Connection Limit:**

    ```bash
    docker-compose exec postgres psql -U app -d orders -c "SHOW max_connections;"
    docker-compose exec postgres psql -U app -d orders -c "SELECT count(*) FROM pg_stat_activity;"
    ```

4.  **Restart if Necessary:**
    ```bash
    docker-compose restart postgres
    # Wait for services to reconnect
    ```

---

## Emergency Contacts

| Role                 | Responsibility                           |
| -------------------- | ---------------------------------------- |
| **On-Call Engineer** | First responder for all alerts           |
| **Platform Team**    | Kafka, PostgreSQL, infrastructure issues |
| **Application Team** | Business logic, service-specific bugs    |

---

## Quick Reference

```bash
# View all service logs
docker-compose logs -f

# Restart a specific service
docker-compose restart <service-name>

# Scale a service
docker-compose up -d --scale <service-name>=N

# Access PostgreSQL CLI
docker-compose exec postgres psql -U app -d orders

# Check Kafka topics
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Reset consumer group offset (DANGEROUS - use with caution)
docker-compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
    --group <group-id> --topic <topic> --reset-offsets --to-earliest --execute
```
