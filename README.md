# Fraud Detection Engine

A sample **real-time fraud detection** pipeline built with **Java 25**, **Spring Boot 3.4**, **Apache Kafka**, **Apache Flink 1.20**, and **Redis**. It mirrors patterns used in large-scale card and payments systems: ingest transactions, stream processing for velocity rules, a Redis hot feature store for daily spend, and downstream alerting with scored dispositions.

## Tech stack

| Layer | Technology |
| --- | --- |
| Core API & integration | Spring Boot 3.4, Spring Kafka, Spring Data Redis |
| Stream processing | Apache Flink 1.20, Flink Kafka connector 3.3.x |
| Messaging | Apache Kafka (topic names in `KafkaConfig`) |
| Hot features | Redis (`StringRedisTemplate`), keys `user:v1:daily_total:{userId}` |
| Serialization | Jackson (JSON), shared conventions for Spring Kafka and Flink sinks |

## Architecture (high level)

1. **Ingest** — `TransactionEvent` records are produced to Kafka topic **`transactions-inbound`** (`TransactionKafkaProducer`; constant `KafkaConfig.TOPIC_TRANSACTIONS_INBOUND`).
2. **Streaming** — **Flink** consumes `transactions-inbound`, uses **event-time** watermarks (bounded out-of-orderness), keys by `userId`, runs **`VelocityKeyedProcessFunction`**, and emits **`FraudAlert`** JSON to **`fraud-alerts`** (`FraudVelocityFlinkJob` via `FraudFlinkJobApplication`). Operators use **`name()`** / **`uid()`** where the API allows (savepoints).
3. **Hot store** — **`DailySpendRedisService`** maintains daily spend with TTL to end of calendar day (zone from `fraud.redis.zone-id`).
4. **Alerting** — **`FraudAlertKafkaListener`** consumes **`fraud-alerts`**, uses **`MockFraudScoreService`**, assigns **`Disposition`** (`BLOCK` / `CHALLENGE` / `ALLOW`), simulates MFA on `CHALLENGE`, and logs **`FraudDecision`**.

## Repository layout (main files)

| Path | Purpose |
| --- | --- |
| `pom.xml` | Maven build; Spring Boot repackage with `layout=ZIP` + explicit `mainClass` for **PropertiesLauncher** (Flink entry in Docker) |
| `Dockerfile` | Multi-stage: Maven → `eclipse-temurin:25-jre`, single `app.jar` |
| `docker-compose.yml` | Kafka (official `apache/kafka`, KRaft), Redis, `app`, `flink-job` |
| `.dockerignore` | Excludes `target/`, `.git`, `.cursor`, etc. |
| `src/main/resources/application.yml` | Spring + `fraud.*` |
| `.cursor/rules/*.mdc` | Cursor project rules |
| `.cursor/agents/*.md` | Optional subagent prompts |

## Prerequisites

- **JDK 25** and **Maven 3.9+** for local builds
- **Docker** + **Docker Compose** to run the full stack
- Without Docker: run **Kafka** (`localhost:9092`) and **Redis** (`localhost:6379`) yourself. Topics **`transactions-inbound`** and **`fraud-alerts`** — auto-created when using the Compose Kafka settings.

## Docker Compose (all services)

From the repo root (where `pom.xml` and `docker-compose.yml` live):

```bash
docker compose up -d --build
```

If you see **`unknown command: docker compose`** or **`unknown shorthand flag: 'd'`**, your Docker CLI does not include the **Compose V2 plugin**. Use the **standalone** command (hyphen) instead:

```bash
docker-compose up -d --build
```

Install it if needed: `brew install docker-compose`. For **`docker compose`** (space), install/update **Docker Desktop** (`brew install --cask docker`) and open the app so the plugin is available.

| Service | Role |
| --- | --- |
| `kafka` | Official **`apache/kafka`** (KRaft, no ZooKeeper); internal bootstrap **`kafka:9092`**; host port **9092** |
| `redis` | Redis 7 Alpine; host port **6379** |
| `app` | Spring Boot **`FraudDetectionApplication`** — builds image `fraud-detection-engine:latest` |
| `flink-job` | Same image; runs `java -Dloader.main=com.fraud.detection.flink.FraudFlinkJobApplication -jar /app/app.jar` |

Stop:

```bash
docker compose down
# or: docker-compose down
```

Logs:

```bash
docker compose logs -f app flink-job
# or: docker-compose logs -f app flink-job
```

Containers set `KAFKA_BOOTSTRAP_SERVERS` / `SPRING_KAFKA_BOOTSTRAP_SERVERS` to `kafka:9092` and Redis host to `redis`. If a **host** Kafka client gets broker metadata errors, use clients **inside** the network or adjust advertised listeners.

**Sample traffic:** Nothing publishes transactions automatically. Produce JSON **`TransactionEvent`** messages to **`transactions-inbound`** (e.g. Kafka console producer in the Kafka container).

## Build (local)

```bash
mvn compile
mvn package -DskipTests
```

## Run (local, no Docker)

With Kafka and Redis already running:

**Spring Boot**

```bash
mvn spring-boot:run
```

or:

```bash
java -jar target/fraud-detection-engine-0.0.1-SNAPSHOT.jar
```

**Flink velocity job** (separate terminal; `exec-maven-plugin` main: `FraudFlinkJobApplication`)

```bash
mvn exec:java
```

## Configuration

Primary file: **`src/main/resources/application.yml`**. Override via environment variables (defaults in YAML).

| Area | Notes |
| --- | --- |
| **Kafka bootstrap** | `KAFKA_BOOTSTRAP_SERVERS` → `spring.kafka.bootstrap-servers` |
| **Topic names** | Only in **`com.fraud.detection.config.KafkaConfig`** (`TOPIC_TRANSACTIONS_INBOUND`, `TOPIC_FRAUD_ALERTS`) — not duplicated under `fraud.kafka` in YAML |
| **Consumer groups** | `fraud.kafka.consumer-group-id` (`FRAUD_KAFKA_CONSUMER_GROUP`), `fraud.kafka.alerts-listener-consumer-group-id` (`FRAUD_ALERTS_LISTENER_GROUP`) |
| **Flink** | `fraud.flink.parallelism` (`FRAUD_FLINK_PARALLELISM`); velocity `fraud.velocity.*` (`FRAUD_VELOCITY_*`) |
| **Redis** | `REDIS_HOST`, `REDIS_PORT`, `SPRING_DATA_REDIS_*`, `REDIS_TIMEOUT`; fraud `fraud.redis.*` (`FRAUD_DAILY_SPEND_LIMIT_USD`, `FRAUD_REDIS_ZONE_ID`) |

**`fraud.*`** maps to **`FraudDetectionProperties`**.

## Key packages

| Package | Role |
| --- | --- |
| `com.fraud.detection` | `FraudDetectionApplication` |
| `com.fraud.detection.model` | `TransactionEvent`, `FraudAlert`, `FraudDecision`, `Disposition` |
| `com.fraud.detection.config` | `KafkaConfig`, `FraudDetectionProperties`, `KafkaProducerConfig`, `FraudAlertKafkaConsumerConfig` |
| `com.fraud.detection.producer` | `TransactionKafkaProducer` |
| `com.fraud.detection.flink` | `FraudFlinkJobApplication`, `FraudVelocityFlinkJob`, `VelocityKeyedProcessFunction` |
| `com.fraud.detection.flink.serialization` | Flink Kafka JSON serde (Jackson) |
| `com.fraud.detection.redis` | `DailySpendRedisService` |
| `com.fraud.detection.alerting` | `FraudAlertKafkaListener`, `MockFraudScoreService` |

## Cursor

| Type | Files |
| --- | --- |
| **Rules** (`.cursor/rules/`) | `java-spring-standards.mdc`, `infrastructure-event-logic.mdc`, `defensive-engineer-security.mdc` |
| **Subagents** (`.cursor/agents/`) | `dry-architect.md`, `sdet-qa-automation.md`, `product-owner-fraud.md`, `security-auditor.md` |

## Disclaimer

Mock scoring and simplified rules are for **demonstration** only. Production fraud systems require governance, model lifecycle, security, and compliance controls not shown here.
