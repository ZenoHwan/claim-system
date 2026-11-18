# Design

## Context

Greenfield. The brief fixes Angular for the frontend, allows Java/Spring Boot or C#/.NET for the backend, and says the platforms use both REST and Kafka. It is a time-boxed assessment (5 hours) that weighs the backend most heavily and expects every decision to be defensible. See `proposal.md` for motivation and the specs for required behaviour.

Diagrams live in `docs/` and are the visual form of this document:
`architecture-c4.md`, `er-diagram.md`, `sequence-diagrams.md`, `state-machine.md`, `user-flows.md`.

## Goals / Non-Goals

**Goals:**
- A claim can travel the whole lifecycle through real services, a real broker and real databases, started locally with one compose command plus the apps.
- Claim handling never waits on, or fails because of, reporting or notifications.
- Downstream data is correct under duplicate delivery, replays and a broker outage.
- Every rule with logic is covered by a test written before the code.

**Non-Goals:**
- Production deployment, TLS, an API gateway, an external identity provider.
- Bank integration, multiple payments per claim, currency conversion.
- Manager sign-off above an authority limit and claimant withdrawal (drawn in `docs/state-machine.md` only).
- Frontend automated tests.

## Decisions

### D1. Java 21 and Spring Boot, one multi-module Maven build
Modules: `claim-events` (event records only), `claims-service`, `reporting-service`, `notification-service`. `frontend/` and `docs/` sit beside them.
- *Alternative: C#/.NET.* Equally allowed; Spring Boot chosen by the candidate.
- *Alternative: one repo per service.* Rejected: nothing to gain for one developer, and the shared event module would need publishing.

### D2. Three services, split by consistency need
- `claims-service` is the system of record and the only service that accepts commands. It also owns sign-in, documents and payment.
- `reporting-service` owns a read model for workload, performance and exposure.
- `notification-service` owns the inbox and the live push.

Reporting and notification can lag or be down without affecting a claim. They never query the claims database.
- *Alternative: one service with consumers inside.* Less wiring and safer in a demo; this was the AI's recommendation. The candidate chose to split.
- *Alternative: also split identity, documents and payment.* Rejected for the time box: each needs its own transaction story with the claim.

### D3. REST for anything a user waits on, Kafka for anything that follows
Commands and queries are REST because the caller needs a result or a clear error. Everything that happens because a claim changed (read models, notifications) is driven by events on one topic, `claim-events`, keyed by claim id so one claim's events stay ordered.
- *Alternative: services call each other over REST.* Rejected: claim handling would then fail when reporting or notification is down.

### D4. Transactional outbox with a polling relay
Each state change writes the claim, its history row and an `outbox_event` row in one database transaction. A scheduled relay in `claims-service` reads unpublished rows oldest first (`FOR UPDATE SKIP LOCKED`), publishes them, and stamps `published_at`. If the broker is down, rows wait and the relay retries; users are unaffected.
- *Alternative: publish to Kafka inside the request.* Rejected: a commit without a publish, or a publish without a commit, is possible.
- *Alternative: Debezium change data capture.* Better at scale; one more container to run and explain. Listed as future work.

### D5. Snapshot events, JSON, defined once
Events are Java records in `claim-events`, serialised as JSON. Each carries an envelope (event id, type, occurred-at, claim version) and a full claim snapshot. Consumers need nothing else.
- *Alternative: thin events plus a callback to claims-service.* Rejected: reintroduces the runtime coupling D3 removes.
- *Alternative: Avro with a schema registry.* Stronger contract evolution; future work.

### D6. Idempotent consumers, dead-letter topic
Delivery is at least once. Each consumer records processed event ids in `processed_event` in the same transaction as its own write. `reporting-service` additionally ignores an event whose claim version is not newer than the row it holds. After bounded retries with backoff a message goes to `claim-events.dlt`.
- *Alternative: exactly-once Kafka transactions.* Does not cover the database write; idempotency is still required.

### D7. Database per service, PostgreSQL, Flyway
One PostgreSQL container hosting `claims_db`, `reporting_db` and `notification_db`. No foreign keys or joins across them. Schema is versioned SQL migrations. Tables are in `docs/er-diagram.md`.
- *Alternative: one shared database.* Rejected: it hides coupling the split is meant to remove.
- Only tables that are used get a migration. The exceptions the candidate asked for are `claim_document` and `payment`, both of which are built.

### D8. Reporting keeps one row per claim and aggregates on read
`claim_summary` is upserted from each event. Workload, performance and exposure are SQL aggregates over it.
- *Alternative: pre-aggregated counter tables.* Rejected: counters drift under replay and duplicate delivery; a per-claim row with a version cannot.

### D9. State machine in the domain, optimistic locking for races
Permitted transitions and their guards live in the claim aggregate and are unit-tested directly. The claim carries a `version` column; a stale write is refused with 409, which is how two officers picking up the same claim is resolved.
- *Alternative: pessimistic row lock on pick-up.* Works, but holds a lock across the request and gives a worse error story.

### D10. Reserve as its own history
Each reserve change is a new row; the newest is current. An initial reserve equal to the claimed amount is created at submission, revised at assessment, and released to zero on rejection or settlement. This is what makes exposure non-zero for unassessed claims.
- *Alternative: a reserve column on the claim.* Loses the history a claims manager would ask for.

### D11. Thin payment
Approval creates one `payment` row in `REQUESTED`. Recording method and reference moves it to `PAID` and the claim to `SETTLED`. A unique constraint on `payment.claim_id` is the double-payment guard. No bank account details are stored.
- *Alternative: a payment service with a Kafka round-trip.* The right long-term shape (see `docs/sequence-diagrams.md` section 4); too much for the time box.

### D12. Documents: metadata in the database, bytes on a local volume
`claim_document` holds metadata and a server-generated storage key. Files are written under a configured directory. Type is checked from the file's leading bytes, size is capped at 10 MB, and download is authorised per claim.
- *Alternative: MinIO or S3.* Same table design; one more container. Future work.
- *Alternative: bytes in PostgreSQL.* Simple, but bloats backups and the claims database.

### D13. Authentication: token issued by claims-service, verified everywhere
`claims-service` checks credentials (BCrypt) and issues a signed JWT with user id, role, market and team. The sign-in request states which door it came from and the server refuses a role that does not match. Each service validates the token as a resource server using a shared signing key supplied by configuration.
- *Alternative: a role header trusted by services.* Indefensible once there is more than one service.
- *Alternative: an identity provider (Keycloak) with asymmetric keys.* Correct for production; future work. The shared key is a known shortcut.

### D14. Contract is generated from the backend
Each service publishes OpenAPI from its code. Angular API clients are generated from those documents, so contract drift fails the frontend build. Errors use the problem-details format.
- *Alternative: hand-written OpenAPI first.* More ceremony than one developer needs here.

### D15. One Angular application, two lazy areas, two login screens
Standalone components and signals, Angular Material. `/login` leads to `/claims/**` for claimants; `/admin/login` leads to `/admin/**` for staff. Route guards read the role from the token; the server enforces the same rules. The dev server proxies `/api/claims`, `/api/reports` and `/api/notifications` to the three services.
- *Alternative: two separate applications.* The brief requires one.

### D16. Live updates by server-sent events
`notification-service` exposes an SSE stream per signed-in user. The frontend updates the bell and refreshes the open claim when a notification for it arrives.
- *Alternative: WebSockets.* Two-way traffic is not needed.
- *Alternative: polling.* Simpler, but it does not demonstrate the asynchronous path end to end.

### D17. Working method
- Test-driven with red, green, refactor for anything with logic: state transitions, reserve rules, policy checks, authorisation rules, outbox writing, consumer idempotency, read-model queries. Not for scaffolding, configuration, migrations or Angular templates.
- Integration tests use Testcontainers against real PostgreSQL and Kafka.
- One commit per green cycle (test and code together); refactors as separate commits; Conventional Commits format with scopes `claims`, `reporting`, `notification`, `events`, `frontend`, `docs`.
- `AI_JOURNAL.md` gets an entry at each decision point.

## Risks / Trade-offs

- [Three services plus Kafka is a lot to start in a live demo] → One compose file, health checks, a README with exact start order, and a rehearsed run.
- [Five-hour cap against this scope] → Build order is claims, reporting, notification, Angular. Cut order if short: notification UI, then manager dashboard UI, then notification-service itself.
- [Dashboards are eventually consistent] → Every reporting response carries an as-of time; the UI shows it.
- [Dead-lettered events need a person to look at them] → Accepted; an admin view over the dead-letter topic is future work.
- [Published outbox rows are never purged] → Accepted; they double as an audit trail. A cleanup job is future work.
- [Shared JWT signing key across services] → Accepted for local use; replaced by an identity provider with asymmetric keys in production.
- [Snapshot events couple consumers to the claim's shape] → Events are defined in one module and consumers ignore unknown fields.
- [Polling relay adds up to a second of delay and constant light database load] → Acceptable at this scale; change data capture is the upgrade path.
- [Local-disk documents do not survive a multi-instance deployment] → Storage sits behind one small class so that a bucket can replace it.

## Open Questions

- Should officers see workload figures for their own claims, or is the dashboard manager-only as specified? It does not change the data model.
- How long should published outbox rows and read notifications be retained?
