# Lead API – CRM to RabbitMQ Event Pipeline

A Spring Boot REST API that receives lead-creation events from an Oracle APEX CRM and publishes them to RabbitMQ for asynchronous, decoupled processing.

## Overview

This project demonstrates an **event-driven integration pattern**: instead of a CRM directly and synchronously calling every downstream system it needs to notify, it fires a single event into a message queue. Any number of independent consumers can then react to that event — without the CRM ever knowing they exist.

```
Oracle APEX (CRM)
      │  AFTER INSERT trigger on lead creation
      ▼
PL/SQL → APEX_WEB_SERVICE.MAKE_REST_REQUEST (HTTPS POST)
      │
      ▼
ngrok tunnel (public HTTPS → localhost)
      │
      ▼
Spring Boot REST API  (/api/leads)
      │
      ▼
RabbitMQ  (lead-queue)
      │
      ▼
Consumer service(s) — currently logs the event;
designed to be extended (notifications, persistence, analytics, etc.)
```

## Why this architecture

- **Decoupling** – the CRM doesn't need to know what happens after a lead is created. New consumers can be added later with zero changes to APEX or the API.
- **Resilience** – if a downstream consumer is temporarily down, messages wait safely in the queue instead of being lost.
- **Real-world relevance** – this mirrors how production systems integrate legacy/low-code platforms (like Oracle APEX) with modern event-driven backends.

## Tech stack

- **Java 21**
- **Spring Boot** (Spring Web, Spring AMQP)
- **RabbitMQ** (containerized via Docker)
- **Oracle APEX** (PL/SQL trigger + `APEX_WEB_SERVICE`)
- **ngrok** (secure tunnel from a public URL to a local API during development)
- **Maven**

## How it works

1. A new lead is submitted through an Oracle APEX form, inserting a row into `SALES_PIPELINE3`.
2. An `AFTER INSERT` database trigger fires automatically and builds a JSON payload from the new row.
3. The trigger calls out to this API's `/api/leads` endpoint over HTTPS using `APEX_WEB_SERVICE.MAKE_REST_REQUEST`.
4. The Spring Boot API (`LeadController`) receives the JSON, converts it into a `Lead` object, and publishes a message to the `lead-queue` RabbitMQ queue via `RabbitTemplate`.
5. A `@RabbitListener`-based consumer (`LeadConsumer`) picks up the message asynchronously and processes it — currently logging it, with the architecture in place to extend it (e.g. email/Slack alerts, writing to a database, updating a live dashboard).

## Project structure

```
lead-api/
├── src/main/java/lead_api/
│   ├── LeadApiApplication.java   # Spring Boot entry point
│   ├── Lead.java                 # Data model for an incoming lead
│   ├── LeadController.java       # REST endpoint: POST /api/leads
│   ├── RabbitConfig.java         # Declares the lead-queue
│   └── LeadConsumer.java         # Listens on lead-queue
├── src/main/resources/
│   └── application.properties    # RabbitMQ connection config
└── pom.xml
```

## Running it locally

**Prerequisites:** Java 21, Maven, Docker

1. Start RabbitMQ:
   ```bash
   docker run -d --hostname rabbit-host --name rabbitmq-test -p 5672:5672 -p 15672:15672 rabbitmq:3-management
   ```
   Management dashboard available at `http://localhost:15672` (guest/guest).

2. Run the API:
   ```bash
   mvn spring-boot:run
   ```

3. Send a test lead:
   ```bash
   curl -X POST http://localhost:8080/api/leads \
     -H "Content-Type: application/json" \
     -d '{"name":"John Doe","email":"john@example.com","company":"Acme Corp"}'
   ```

4. (Optional) Expose it publicly for a real Oracle APEX connection:
   ```bash
   ngrok http 8080
   ```
   Then point the APEX trigger's `p_url` at the generated `https://*.ngrok-free.dev/api/leads` URL.

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
        p_url         => '<your-ngrok-or-deployed-url>/api/leads',
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

- Replace the demo consumer with real notification logic (email/Slack)
- Add a second, independent consumer to demonstrate fan-out
- Persist events to a database for analytics/audit history
- Add a Kafka-based variant of the same pipeline for comparison
- Deploy the API to a small cloud instance to remove the ngrok dependency
