# Lead API – CRM Event-Driven Integration (RabbitMQ + Kafka)

A Spring Boot REST API that receives lead-creation events from an Oracle APEX CRM and publishes them to **both RabbitMQ and Kafka** in parallel, demonstrating a dual-broker, environment-aware event-driven architecture.

## Overview

This project demonstrates an **event-driven integration pattern**: instead of a CRM directly and synchronously calling every downstream system it needs to notify, it fires a single event that fans out to independent message brokers. Any number of consumers can react to that event — without the CRM, or even the brokers, knowing about each other.

```
Oracle APEX (CRM)
      │  AFTER INSERT trigger on lead creation
      ▼
PL/SQL → APEX_WEB_SERVICE.MAKE_REST_REQUEST (HTTPS POST)
      │
      ▼
Spring Boot REST API  (/api/leads)
      │
      ├──────────────────────┬─────────────────────────┐
      ▼                      ▼                         
RabbitMQ (lead-queue)    Kafka (lead-events)
      │                      │
      ▼                      ▼
RabbitMQ Consumer       Kafka Consumer
(LeadConsumer)          (KafkaLeadConsumer)
```

## Why this architecture

- **Decoupling** – the CRM doesn't need to know what happens after a lead is created. New consumers can be added later with zero changes to APEX or the API.
- **Resilience** – if a downstream consumer is temporarily down, messages wait safely in the broker instead of being lost.
- **Dual-broker design** – publishing to both RabbitMQ and Kafka in the same flow demonstrates when each tool fits: RabbitMQ for simple task-queue style delivery, Kafka for durable, replayable event streams that multiple independent consumer groups can read.
- **Environment-aware configuration** – the same codebase behaves differently depending on where it runs (see "Environment Profiles" below), a pattern used in real production systems to avoid hardcoding infrastructure details.

## Tech stack

- **Java 21**
- **Spring Boot** (Spring Web, Spring AMQP, Spring Kafka)
- **RabbitMQ** — local via Docker, or [CloudAMQP](https://www.cloudamqp.com/) (managed, free tier) in deployed environments
- **Apache Kafka** — local via Docker Compose (Zookeeper + Kafka broker)
- **Oracle APEX** (PL/SQL trigger + `APEX_WEB_SERVICE`)
- **ngrok** (secure tunnel from a public URL to a local API during development)
- **Maven**

## How it works

1. A new lead is submitted through an Oracle APEX form, inserting a row into `SALES_PIPELINE3`.
2. An `AFTER INSERT` database trigger fires automatically and builds a JSON payload from the new row.
3. The trigger calls out to this API's `/api/leads` endpoint over HTTPS using `APEX_WEB_SERVICE.MAKE_REST_REQUEST`.
4. The Spring Boot API (`LeadController`) receives the JSON, converts it into a `Lead` object, and publishes the event to:
   - **RabbitMQ** (`lead-queue`) via `RabbitTemplate` — always
   - **Kafka** (`lead-events`) via `KafkaTemplate` — only when running with the `local` profile (see below)
5. Two independent consumers process the event asynchronously:
   - `LeadConsumer` (`@RabbitListener`) — reacts to the RabbitMQ message
   - `KafkaLeadConsumer` (`@KafkaListener`) — reacts to the Kafka message

   Both currently log the event, with the architecture in place to extend them (e.g. email/Slack alerts, writing to a database, updating a live dashboard).

## Environment profiles

The same code runs in two modes, controlled by `spring.profiles.active`:

| Profile | RabbitMQ | Kafka | Used for |
|---|---|---|---|
| `local` (default) | CloudAMQP or local Docker | Local Docker | Development on a laptop |
| `prod` | CloudAMQP | **Disabled** | Deployed environments (e.g. Render) |

Kafka is intentionally **omitted in `prod`**, since a deployed API can't reach a Kafka broker running on a developer's laptop. `LeadController` uses `@Autowired(required = false)` on its `KafkaTemplate`, and `KafkaConfig`/`KafkaLeadConsumer` are annotated `@Profile("local")`, so the application starts cleanly either way — publishing to Kafka only when it's actually available.

This mirrors a real production pattern: in an actual deployment, Kafka would point at a managed service (e.g. Confluent Cloud) rather than being disabled — the `prod` profile here is a placeholder for that, kept RabbitMQ-only to stay within free-tier constraints.

## Configuration & secrets

Connection details are **not hardcoded** — they're read from environment variables:

```properties
spring.rabbitmq.host=${RABBITMQ_HOST}
spring.rabbitmq.port=${RABBITMQ_PORT}
spring.rabbitmq.username=${RABBITMQ_USERNAME}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
spring.rabbitmq.virtual-host=${RABBITMQ_VHOST}
```

Set these locally (PowerShell, persisted at the user level):
```powershell
[System.Environment]::SetEnvironmentVariable('RABBITMQ_HOST', '<your-cloudamqp-host>', 'User')
[System.Environment]::SetEnvironmentVariable('RABBITMQ_PORT', '5671', 'User')
[System.Environment]::SetEnvironmentVariable('RABBITMQ_USERNAME', '<your-username>', 'User')
[System.Environment]::SetEnvironmentVariable('RABBITMQ_PASSWORD', '<your-password>', 'User')
[System.Environment]::SetEnvironmentVariable('RABBITMQ_VHOST', '<your-vhost>', 'User')
```

In deployed environments (e.g. Render), the same variables are set in the platform's dashboard rather than in code.

## Project structure

```
lead-api/
├── src/main/java/lead_api/
│   ├── LeadApiApplication.java   # Spring Boot entry point
│   ├── Lead.java                 # Data model for an incoming lead
│   ├── LeadController.java       # REST endpoint: POST /api/leads — publishes to RabbitMQ + Kafka
│   ├── RabbitConfig.java         # Declares the lead-queue
│   ├── LeadConsumer.java         # @RabbitListener on lead-queue
│   ├── KafkaConfig.java          # Manual producer/consumer factory beans (local profile only)
│   └── KafkaLeadConsumer.java    # @KafkaListener on lead-events (local profile only)
├── src/main/resources/
│   ├── application.properties        # Selects the active profile
│   ├── application-local.properties  # RabbitMQ + Kafka config for local development
│   └── application-prod.properties   # RabbitMQ-only config for deployed environments
├── docker-compose.yml            # Zookeeper + Kafka broker for local development
└── pom.xml
```

## Running it locally

**Prerequisites:** Java 21, Maven, Docker, environment variables set as above

1. Start Kafka (Zookeeper + broker):
   ```bash
   docker compose up -d
   ```

2. (If using local RabbitMQ instead of CloudAMQP) start it separately:
   ```bash
   docker run -d --hostname rabbit-host --name rabbitmq-test -p 5672:5672 -p 15672:15672 rabbitmq:3-management
   ```

3. Run the API:
   ```bash
   mvn spring-boot:run
   ```

4. Send a test lead:
   ```bash
   curl -X POST http://localhost:8080/api/leads \
     -H "Content-Type: application/json" \
     -d '{"name":"John Doe","email":"john@example.com","company":"Acme Corp"}'
   ```
   Expect to see four log lines: published to RabbitMQ, published to Kafka, and both consumers receiving the message.

5. (Optional) Expose it publicly for a real Oracle APEX connection:
   ```bash
   ngrok http 8080
   ```
   Then point the APEX trigger's `p_url` at the generated `https://*.ngrok-free.dev/api/leads` URL.

## Deploying

The API is deployable as-is to any platform that supports a Java/Maven build (e.g. Render, Railway):

- **Build command:** `./mvnw clean package -DskipTests`
- **Start command:** `java -jar target/lead-api-0.0.1-SNAPSHOT.jar`
- **Environment variables:** `SPRING_PROFILES_ACTIVE=prod`, plus the five `RABBITMQ_*` variables above

Once deployed, update the Oracle APEX trigger's `p_url` to the platform's permanent URL instead of an ngrok tunnel — removing the need to update the trigger every time a local tunnel restarts.

## Example: Oracle APEX trigger

```sql
CREATE OR REPLACE TRIGGER trg_lead_notify
AFTER INSERT ON sales_pipeline3
FOR EACH ROW
DECLARE
    l_response  CLOB;
    l_payload   VARCHAR2(4000);
BEGIN
    l_payload := '{'
        || '"name":"'    || REPLACE(:NEW.LEAD_NAME, '"', '\"')    || '",'
        || '"email":"'   || REPLACE(:NEW.EMAIL, '"', '\"')         || '",'
        || '"company":"' || REPLACE(:NEW.COMPANY_NAME, '"', '\"')  || '"'
        || '}';

    apex_web_service.g_request_headers(1).name  := 'Content-Type';
    apex_web_service.g_request_headers(1).value := 'application/json';

    l_response := apex_web_service.make_rest_request(
        p_url         => '<your-deployed-or-ngrok-url>/api/leads',
        p_http_method => 'POST',
        p_body        => l_payload
    );
EXCEPTION
    WHEN OTHERS THEN
        NULL; -- lead creation should not fail if the notification call fails
END;
/
```

## Related project

The Oracle APEX CRM (sales pipeline / interactive report) that triggers this pipeline lives in a separate repository.

## Possible extensions

- Replace the demo consumers with real notification logic (email/Slack)
- Add a managed Kafka service (e.g. Confluent Cloud) to the `prod` profile, removing the local-only restriction
- Persist events to a database for analytics/audit history
- Add a second, independent consumer per broker to demonstrate fan-out
- Add integration tests using Testcontainers for RabbitMQ and Kafka
