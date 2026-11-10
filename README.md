# Agentic Supply Chain: Java + Kafka + Spring AI

An event-driven, multi-agent system built from the "Intelligent Supply Chain" flow:

```
Supplier Delay Event -> Kafka -> Supervisor Agent --+-> Inventory Agent        (stock & alternatives)
                                                    +-> Impact Analysis Agent  (affected orders)   } run in parallel
                                                    +-> Customer Service Agent (notifications, credit)
                                                              |
                                                    approval gate -> Human-in-the-Loop (refunds / big reroutes)
                                                              |
                                                    decision -> Kafka topic agent.decisions
```

## Run

```bash
docker compose up -d          # Kafka on :9092 and MySQL on :3306
mvn spring-boot:run           # Java 17+, app on :8080
```

No MySQL? Use the in-memory H2 profile (data lost on restart, Kafka still required):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Import `agentic-supply-chain.postman_collection.json` into Postman, or:

```bash
# medium impact -> AUTO_EXECUTED
curl -X POST localhost:8080/api/events/supplier-delay -H 'Content-Type: application/json' \
  -d '{"supplierId":"SUP-A","sku":"SKU-1001","delayDays":5,"reason":"port congestion"}'

# critical impact -> PENDING_APPROVAL
curl -X POST localhost:8080/api/events/supplier-delay -H 'Content-Type: application/json' \
  -d '{"supplierId":"SUP-A","sku":"SKU-3003","delayDays":12,"reason":"customs hold"}'

curl localhost:8080/api/decisions
curl localhost:8080/api/approvals                      # the human's queue
curl -X POST localhost:8080/api/approvals/<id>/approve # or /reject
```

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/events/supplier-delay` | Publish a delay event to Kafka (202) |
| GET | `/api/decisions`, `/api/decisions/{id}` | Supervisor decisions |
| GET | `/api/approvals` | Decisions waiting for a human |
| POST | `/api/approvals/{id}/approve` or `/reject` | Human decision (404 unknown, 409 not pending) |

## Decision rules (demo data in `data/`)

- Stock serves orders earliest-promise first. An order stock cannot cover is **impacted** only if its promised date is before the delayed shipment arrives.
- Severity by order value at risk: below 1 LOW, below 20,000 MEDIUM, below 60,000 HIGH, otherwise CRITICAL.
- Telling customers: always automatic. Reroute to another supplier: needs approval at HIGH or above. Goodwill credit (5% of an order that is 3+ days late): always needs approval.

## Using a real LLM

Default: deterministic wording, no key needed. To let Spring AI polish summaries and customer emails:

```bash
export OPENAI_API_KEY=sk-...
export AGENTS_LLM_ENABLED=true
```

The model only rewords facts the code already computed, and the pipeline falls back to the plain text if the call fails. Money, stock and approval decisions never depend on the model.

## Design notes

- `agent/`, `service/`, `model/`, `data/` are plain Java with no Spring or Kafka imports. `config/` wires them in.
- Kafka is at-least-once, so `DecisionService` skips an `eventId` it has already processed.
- Events are keyed by SKU, so delays for one SKU are handled in order.
- A failing message is retried 3 times, then parked on `supplier.delay.events.DLT`. Malformed JSON goes there immediately.
- `mvn test` runs `DecisionFlowTest`, which needs no Kafka or database (it uses `InMemoryDecisionStore`).

## Persistence (MySQL)

Decisions are stored in a `decisions` table through `DecisionStore` (`JpaDecisionStore`). That gives you:

- **Restart safety:** pending approvals are still there after the app restarts.
- **Multiple instances:** every instance reads and writes the same table.
- **Exactly-once processing:** a unique key on `event_id` rejects a duplicate event, even if two consumers race.
- **Safe approvals:** approve/reject is one `UPDATE ... WHERE status = 'PENDING_APPROVAL'`, so two people clicking at once cannot both win.

Connection settings come from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults match `docker-compose.yml`).
`ddl-auto: update` creates the table for you in development. For production, manage the schema with Flyway
and set it to `validate`.

Check the data:

```bash
docker exec -it agentic-mysql mysql -usupplychain -psupplychain supplychain \
  -e "select decision_id, sku, severity, status from decisions order by created_at desc;"
```

Known gap: the decision is saved first and published to Kafka second. If the app crashes between the two,
the redelivered event is skipped as a duplicate and that decision is never published to `agent.decisions`.
A transactional outbox table closes this gap if you need strict delivery.

## Docker

```bash
docker build -t agentic-supply-chain:0.0.1 .
```

The image is a multi-stage build that runs as a non-root user. Settings come from environment variables
(`KAFKA_BOOTSTRAP_SERVERS`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AGENTS_LLM_ENABLED`, `OPENAI_API_KEY`).
`docker-compose.yml` only runs Kafka and MySQL for local development. Kafka there advertises `localhost:9092`,
so run the app on your machine, not inside the compose network.

## Kubernetes

Manifests are in `k8s/`: namespace, ConfigMap, Deployment (2 replicas, probes, resource limits, non-root,
read-only filesystem), Service, and a kustomization. Kafka and MySQL are **not** deployed by these files:
point the ConfigMap at your own (Strimzi, MSK, Confluent, RDS, Cloud SQL...).

```bash
# 1. push the image to a registry and set it in k8s/deployment.yaml (or the images: block in kustomization.yaml)
# 2. edit k8s/configmap.yaml (Kafka and DB addresses)
# 3. create the secret (see k8s/secret.example.yaml)
kubectl apply -k k8s/
kubectl -n supply-chain get pods
kubectl -n supply-chain port-forward svc/agentic-supply-chain 8080:80
```

Scaling note: the topic has 3 partitions, so at most 3 instances consume at once. Raise `TOPIC_PARTITIONS` first if
you need more. In production set `TOPIC_REPLICAS` to 3.

## Not included (from the diagram)

Apache Flink stream processing and LangChain4j. Cloud-specific infrastructure (AWS, a managed Kafka or
MySQL) is also not provisioned. Health endpoints for probes are at `/actuator/health/liveness` and `/readiness`.
