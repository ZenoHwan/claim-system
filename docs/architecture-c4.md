# System Architecture (C4)

## Level 1: System Context

```mermaid
C4Context
    title Claims Platform - System Context

    Person(claimant, "Claimant", "Policyholder reporting a motor or property incident")
    Person(officer, "Claims Officer", "Picks up, assesses and decides claims")
    Person(manager, "Claims Manager", "Watches team workload and liability exposure")

    System(platform, "Claims Platform", "Claim intake, processing, tracking and reporting across six APAC markets")

    System_Ext(email, "Email provider", "Stubbed: messages are logged, not sent")
    System_Ext(bank, "Bank / finance system", "Future: executes settlement payments")

    Rel(claimant, platform, "Reports incidents, tracks claims, replies to requests", "HTTPS")
    Rel(officer, platform, "Works the claim queue", "HTTPS")
    Rel(manager, platform, "Views workload and exposure", "HTTPS")
    Rel(platform, email, "Sends notifications", "stub")
    Rel(platform, bank, "Requests payments", "future")
```

## Level 2: Containers

```mermaid
C4Container
    title Claims Platform - Containers

    Person(claimant, "Claimant")
    Person(staff, "Claims Officer / Manager")

    Container_Boundary(platform, "Claims Platform") {
        Container(spa, "Web App", "Angular, Angular Material", "One codebase: claimant area at /claims, staff area at /admin, separate login screens")
        Container(web, "Web server", "nginx", "Serves the built app with long-lived caching of hashed bundles, and routes /api by path to the three services")

        Container(claims, "claims-service", "Java 21, Spring Boot", "System of record. Auth, claim commands and queries, state machine, documents, payment, outbox relay")
        Container(reporting, "reporting-service", "Java 21, Spring Boot", "Builds claim_summary from events. Workload, performance and exposure queries")
        Container(notification, "notification-service", "Java 21, Spring Boot", "Turns events into inbox messages and pushes them over SSE")

        ContainerQueue(kafka, "claim-events", "Kafka topic, keyed by claim id", "Snapshot events for every claim state change, plus a dead-letter topic")

        ContainerDb(claimsDb, "claims_db", "PostgreSQL", "Claims, policies, users, documents metadata, payments, outbox")
        ContainerDb(reportingDb, "reporting_db", "PostgreSQL", "claim_summary read model")
        ContainerDb(notificationDb, "notification_db", "PostgreSQL", "Notification inbox")
        ContainerDb(docs, "Document store", "Local volume (S3 later)", "Uploaded evidence files")
        ContainerDb(cache, "Cache", "Redis", "Follow-ups per user and dashboard reports per market; cleared on change, 60 s expiry as a backstop")
    }

    Rel(claimant, spa, "Uses", "HTTPS")
    Rel(staff, spa, "Uses", "HTTPS")
    Rel(spa, web, "Loaded from, and calls /api on", "HTTPS")

    Rel(web, claims, "Login, claims, policies, follow-ups, uploads", "REST/JSON + JWT")
    Rel(web, reporting, "Dashboard queries", "REST/JSON + JWT")
    Rel(web, notification, "Inbox, mark all read, live updates", "REST + SSE + JWT")

    Rel(claims, claimsDb, "Reads and writes", "JDBC")
    Rel(claims, docs, "Stores and serves files", "File I/O")
    Rel(claims, cache, "Caches Follow-ups, clears them on each claim change", "RESP")
    Rel(reporting, cache, "Caches reports, clears a market on each event", "RESP")
    Rel(claims, kafka, "Publishes from outbox", "Kafka")
    Rel(kafka, reporting, "Consumed by", "Kafka")
    Rel(kafka, notification, "Consumed by", "Kafka")
    Rel(reporting, reportingDb, "Upserts and queries", "JDBC")
    Rel(notification, notificationDb, "Reads and writes", "JDBC")
```

## Why it is cut this way

| Decision | Reason | Trade-off accepted |
|---|---|---|
| Three services | Writes, dashboards and notifications have different load and consistency needs; reporting and notification can lag or be down without blocking a claim | More wiring and more to start locally than one service |
| REST for commands and queries | A user is waiting for the answer and needs a success or a clear error | None of note |
| Kafka for everything downstream | Notifications and read models happen because a claim changed; they must not slow or fail the change | Dashboards are eventually consistent (seconds) |
| Transactional outbox | The event row commits with the claim change, so an event is never published for a rolled-back write | At-least-once delivery, so consumers must be idempotent |
| Snapshot events | Consumers never call back into claims-service | Larger payloads; schema changes need care |
| Database per service | No hidden coupling through shared tables | No cross-service joins; data is duplicated in read models |
| JWT issued by claims-service, verified by each service | Every service checks identity itself; no shared session | Identity lives in claims-service for now; a dedicated identity provider is future work |
| No API gateway yet | nginx in the frontend container routes `/api` by path, so the browser talks to one origin | A gateway is needed before production for TLS and rate limits |
| Redis for two hot reads only | Follow-ups (every page change, every user) and the dashboard aggregates grow with usage; each is cleared when its data changes | One more container; everything else reads PostgreSQL directly so no other screen can go stale |

## Local runtime

`docker compose up -d --build` starts seven containers: PostgreSQL (one server holding
`claims_db`, `reporting_db` and `notification_db`), Kafka, Redis, the three services, and nginx
serving the built Angular app. nginx looks the services up through Docker's DNS on each request, so
a restarted service is found without restarting nginx. While changing code, the services and the Angular dev server can run on the
host against the PostgreSQL and Kafka containers instead.
