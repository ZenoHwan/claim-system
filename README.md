# Claims Platform

Motor and property claims platform: claimants report and track claims, staff
pick them up and take them to settlement or rejection, managers see workload
and outstanding exposure.

- Design decisions: [`openspec/changes/claims-platform-mvp/design.md`](openspec/changes/claims-platform-mvp/design.md)
- Diagrams: [`docs/`](docs)
- How the AI was directed: [`AI_JOURNAL.md`](AI_JOURNAL.md)
- Trade-offs and next steps: [why not X?](#trade-offs-why-not-x) and [what I would do with more time](#what-i-would-do-with-more-time)

## Layout

| Path | What |
|---|---|
| `claim-events/` | Event records shared by producer and consumers |
| `claims-service/` | System of record: sign-in, claims, documents, payment, outbox relay (port 8080) |
| `reporting-service/` | Workload, performance and exposure read model (port 8081) |
| `notification-service/` | Inbox and live push (port 8082) |
| `frontend/` | Angular app (port 4200) |
| `docker-compose.yml` | The whole stack: PostgreSQL, Kafka, the three services and the frontend |
| `Dockerfile` | One image definition for all three services (`MODULE` build argument) |

## Prerequisites

- JDK 21 (`brew install openjdk@21`) with `JAVA_HOME` pointing at it:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Docker
- Node 22 (frontend only)

## Start

### Everything in Docker (simplest)

Needs only Docker.

```bash
docker compose up -d --build
```

The first build takes a few minutes. Then open http://localhost:4200/login (customers) or
http://localhost:4200/admin/login (staff).

`docker compose ps` should list seven containers: `postgres`, `kafka`, `redis`, `claims-service`,
`reporting-service`, `notification-service` and `frontend`. The `frontend` container is nginx
serving the built Angular app and routing `/api` to the three services, so the browser talks to
one origin.

| Container | Host port |
|---|---|
| `frontend` (app + `/api` routing) | 4200 |
| `claims-service` | 8080 |
| `reporting-service` | 8081 |
| `notification-service` | 8082 |
| `postgres` (`claims_db`, `reporting_db`, `notification_db`) | 5433 |
| `kafka` | 9092 |
| `redis` (cache for Follow-ups, claim lists, the queue and dashboard reports; pub/sub for live notifications) | 6379 |

After changing code, rebuild just that part, for example `docker compose up -d --build claims-service`.
To wipe all data: `docker compose down -v`.

### On the host (faster while changing code)

Needs JDK 21 and Node 22 as well. Stop the app containers first so the ports are free.

1. Infrastructure only:

   ```bash
   docker compose up -d postgres kafka redis
   ```

2. Build and run the tests (integration tests start their own PostgreSQL and Kafka containers):

   ```bash
   ./mvnw verify
   ```

3. Run each service in its own terminal:

   ```bash
   java -jar claims-service/target/claims-service-0.0.1-SNAPSHOT.jar
   ```

   ```bash
   java -jar reporting-service/target/reporting-service-0.0.1-SNAPSHOT.jar
   ```

   ```bash
   java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar
   ```

4. Run the frontend dev server:

   ```bash
   cd frontend && npm install --legacy-peer-deps && npm start
   ```

## A five-minute walkthrough

Use two browser windows (one normal, one private) so both sessions stay signed in.

1. Claimant `aisha@claims.test` at `/login`: **My policies**, open the motor policy to see its cover
   and claim history, then **Make a claim**. Try a bad plate, date or postal code to see the
   field errors, then attach a photo and submit.
2. Officer `officer1.my@claims.test` at `/admin/login`: **Queue**, **Pick up**, then **Request information**.
   The claimant's bell and **Follow-ups** count update without a refresh.
3. Claimant: open **Follow-ups**, reply with a PDF. The officer's **Follow-ups** tab now shows
   one reply to review.
4. Officer: **Assess**, **Approve**, **Record payment**. The claimant sees each step live, and the
   follow-up clears itself once the officer assesses.
5. Manager `manager.my@claims.test`: **Dashboard**. Exposure rises on submission, changes on
   assessment and drops on settlement.
6. Resilience: `docker compose stop kafka`, submit another claim (it is accepted),
   `docker compose start kafka`, and watch the dashboard catch up.

## What is built and what is not

Built: sign-in through two doors, a Redis cache for the hot reads and Redis pub/sub for live notifications across instances, the full claim lifecycle from submission to settlement or
rejection, information requests, document upload, a thin payment record, event publishing through
a transactional outbox, workload / performance / exposure reporting, and live in-app notifications.

| Area | Claimant | Staff |
|---|---|---|
| Policies | **My policies** lists each policy with any claim in progress; a policy page shows its cover, sum insured, market and every claim made on it, with **Make a claim** preselecting it | Policy shown on each claim |
| Claims | Five-step **New claim** with a draft that survives a refresh; one open claim per policy | **Queue** of unassigned claims in the officer's market, **My claims**, a workbench per claim |
| Follow-ups | **Follow-ups** tab lists questions waiting for a reply; its badge counts them | **Follow-ups** tab lists replies to review and questions still with the claimant; its badge counts the replies |
| Notifications | Bell with live push; opening it clears the unread count (stored, so it stays cleared) | Same |
| Reporting | | Managers: workload, performance and exposure dashboard |

**Claim form validation.** Every rule is enforced by claims-service and mirrored in the form, so
each field shows its own message:

| Field | Rule |
|---|---|
| Incident date | A real dd/mm/yyyy date, within the policy's cover and not in the future |
| Description | 20 to 4000 characters |
| Estimated loss | Optional; above 0, at most 2 decimal places |
| Vehicle registration | MY / SG plate format, stored in a standard form (`dua1234` becomes `DUA 1234`) |
| Driver name, address lines | Letters, digits and the punctuation names and addresses use; address line 1 required |
| Postal code | 5 digits for a Malaysian policy, 6 for a Singaporean one |

Free text is trimmed and repeated spaces collapsed before it is checked and stored.

Designed only (see `docs/`): manager sign-off above an authority limit, claimant withdrawal,
payment execution through a bank, multiple payments per claim, currency conversion, real email,
an API gateway, an identity provider. Known shortcuts are marked in the code with `ponytail:`.

## Trade-offs: why not X?

Every choice below has an obvious alternative. For each one: what was chosen, why the alternative
was not, what the choice costs, and what would change it. The decision record is in
[`design.md`](openspec/changes/claims-platform-mvp/design.md).

### Three services, not one

- **Chose:** `claims-service` as the only system of record, with reporting and notifications as
  separate services fed by events.
- **Why not one service:** one application with in-process listeners is less wiring and easier to
  demo. But a slow dashboard query or a notification bug would then share a process, a connection
  pool and a release with claim handling. Splitting by consistency need keeps claims strongly
  consistent and lets everything downstream lag or fail on its own.
- **Cost:** seven containers to run, dashboards a few seconds behind, no joins across services.
- **Would change if:** never back to one; identity, documents and payment stay inside
  `claims-service` until one of them needs its own team or scaling, because each would need its
  own transaction story with the claim.

### Kafka for what follows a change, not REST between services

- **Why not REST calls from `claims-service` to the others:** claim handling would slow down or fail
  whenever reporting or notification is down. With events, `claims-service` does not know who
  listens, and a new consumer needs no change to it.
- **Cost:** delivery is at least once, so every consumer must be idempotent; order is kept per
  claim only (the message key is the claim id).

### Transactional outbox, not publishing inside the request

- **Why not send to Kafka in the request:** the database commit and the publish cannot be atomic. A
  crash between them either loses an event or publishes one for a write that rolled back. The
  outbox row commits with the claim change, and a relay publishes it afterwards.
- **Why not Debezium change data capture:** better at volume (it reads the write-ahead log instead
  of polling), but it adds Kafka Connect and another container to run and explain.
- **Cost:** up to a second of delay and a light, constant poll. Published rows are kept as an audit
  trail and never purged.
- **Would change if:** event volume makes the poll noticeable; then CDC.

### Snapshot events in JSON, not thin events or Avro

- **Why not thin events ("claim X changed"):** every consumer would call back into
  `claims-service` for the details, which brings back the runtime coupling events removed.
- **Why not Avro with a schema registry:** stronger compatibility checks, but one more service. One
  shared Java module, plus consumers that ignore unknown fields, covers a single team.
- **Cost:** larger payloads, and consumers depend on the claim's shape.
- **Would change if:** a second team consumes the topic; then Avro and a registry.

### Idempotent consumers, not exactly-once delivery

- **Why not Kafka exactly-once:** its transactions cover Kafka to Kafka, not the consumer's own
  database write, so idempotency is needed anyway. Each consumer records processed event ids in
  the same transaction as its write. Reporting also ignores an event whose claim version is not
  newer than the row it has. A message that keeps failing goes to `claim-events.dlt` after bounded
  retries.
- **Cost:** a `processed_event` table per consumer, and dead letters need a person (there is no
  admin view yet).

### A database per service, on one PostgreSQL server

- **Why not one shared database:** shared tables are hidden coupling. Renaming a column for claims
  would break reporting at runtime, with nothing to warn about it. Each service owns its database
  and changes it only through its own Flyway migrations.
- **Why one server, not three:** the isolation that matters is ownership, not hardware. Three
  PostgreSQL containers would triple memory locally and teach nothing. In production each database
  can move to its own instance by changing a connection string.
- **Cost:** no foreign keys or joins across services; reporting keeps its own copy of what it needs.

### A row per claim in the read model, not counter tables

- **Why not pre-aggregated counters:** counters drift under replays and duplicate delivery. A row
  per claim, upserted with its version, cannot; the reports are SQL aggregates over it.
- **Would change if:** claims reach the millions; then materialised views or pre-aggregation.

### Optimistic locking, not row locks

- **Why not a pessimistic lock on pick-up:** it holds a database lock across the request and gives
  a worse error. With a `version` column, the second of two officers picking up the same claim gets
  a 409 and an "already picked up" message.
- **Cost:** the officer who loses the race picks another claim.

### One open claim per policy

- **Chose the stricter rule:** a policy carries one claim at a time. A partial unique index makes it
  hold even under simultaneous submissions, and the form disables a policy with a claim in progress.
- **Cost:** in real practice separate incidents on one policy are separate claims. Relaxing the rule
  is one index and one check.

### Reserve as a history, not a column

- **Why not a `reserve` column on the claim:** a claims manager asks how the reserve moved, and a
  column forgets. Each change is a new row; the newest is copied onto the claim for cheap reads.
  With no claimed amount, the first reserve is a share of the sum insured (10% motor, 5% property),
  because a fixed figure means nothing across six currencies.

### A thin payment record, not a payment service

- **Why not a payment service with a bank integration:** the right long-term shape (drawn in
  [`docs/sequence-diagrams.md`](docs/sequence-diagrams.md) section 5), but it brings its own
  failure handling. Here approval creates one payment row, recording it settles the claim, and a
  unique constraint on `payment.claim_id` blocks double payment. No bank details are stored.

### Documents on a volume, not S3 or the database

- **Why not bytes in PostgreSQL:** simple, but it bloats backups of the system of record.
- **Why not S3 or MinIO:** the table design is the same; it is one more container. Storage sits
  behind one class so a bucket can replace the disk. File type is checked from the file's first
  bytes, not its name.
- **Cost:** a local disk does not survive running several instances.

### Tokens from `claims-service`, not an identity provider

- **Why not Keycloak:** correct for production (asymmetric keys, password reset, MFA), but a heavy
  container and setup for a demo. Each service checks the JWT itself; the sign-in also states its
  door, so a claimant account cannot sign in as staff.
- **Cost:** a known shortcut: the three services share one signing key, so any of them could mint a
  token. Tokens last eight hours with no refresh.

### Server-sent events, not WebSockets or polling

- **Why not WebSockets:** the browser never needs to push; SSE is plain HTTP through the same
  nginx routing. The app reads the stream with `fetch` so it can send the token, and reconnects
  after three seconds if the connection drops.
- **Why not polling:** simpler, but it would not show the asynchronous path end to end, and it
  costs requests when nothing has changed.

### Follow-ups derived, not stored

- **Why not a follow-up table with its own status:** a second status can disagree with the claim.
  The Follow-ups lists and counts come from the claim's status and its latest information request,
  so they clear themselves when the claim moves on.

### Validation on the server, mirrored in the form

- **Why not the form only:** the API is the trust boundary; any client can send anything.
- **Why not the server only:** the claimant would see problems only after pressing Submit.
- **Cost:** each rule exists twice, in Java and TypeScript. Integration tests and the Playwright
  suite keep the two in step.

### Redis for the hot reads and live push, not a cache on everything

- **Chose:** Redis does two jobs.
  - **Cache** for the reads that grow with usage:
    - Follow-ups, per user: every signed-in user fetches it on every page change, and it costs a
      query per claim the user has.
    - Each person's claim list (My claims for a claimant, My claims for an officer) and each
      market's unassigned queue.
    - Dashboard reports: workload and performance per market, exposure once. Each aggregates over
      every claim in the read model.
  - **Pub/sub** for live notifications. Kafka hands each event to one `notification-service`
    instance; that instance stores the notification and publishes it on a Redis channel, and every
    instance pushes it to the users connected to it. Live push therefore works with any number of
    instances.
- **Kept current by eviction, not by waiting:** every claim change goes through one method
  (`ClaimChanges.record`). Once the change commits, it clears what shows that claim: the claimant's
  and officer's Follow-ups and claim lists, and the market's queue. Reporting clears a market's
  reports when it applies an event for that market (exposure on every event). The next read
  rebuilds the entry from PostgreSQL. A 60-second expiry is only a backstop.
- **Why not cache the claim detail page too:** it is where an officer or claimant most needs the
  current status, and it has a second way to change (document uploads), so a second place to forget
  an eviction. In a claims workflow, wrong data costs more than a few milliseconds.
- **Why not `@Cacheable` with Java serialisation:** entries are typed JSON (readable with
  `redis-cli`), and nothing but the expected record type is ever built from them.
- **If Redis is down:** cache errors are logged and the database answers; a request waits at most
  the 500 ms Redis timeout. Live notifications are pushed to the users connected to the instance
  that handled the event, and a publish that timed out but arrives later is not pushed twice.
  Every notification is in the inbox either way. Tests pause the Redis container to prove both.
  No service needs Redis to start: `notification-service` retries its subscription every five
  seconds until Redis is there.
- **Cost:** one more container, and a read that started just before a change can put the old value
  back for up to the expiry (marked `ponytail:` in the code). Redis runs as a pure cache: no
  persistence, 256 MB, least recently used keys dropped first.
- **Also cached:** the browser keeps the JavaScript and CSS bundles for a year, because every file
  name carries a content hash; `index.html` is revalidated on each load so a deploy shows up at
  once. API responses stay `no-store` (the Spring Security default).
- **Next uses of the same Redis:** sign-in rate limiting across instances and a shared list of
  revoked tokens.

### End-to-end tests against the real stack, not a mocked API

- **Why not mocks:** a mocked backend would miss exactly what broke during development: nginx
  routing, the live push, Kafka lag and the Follow-ups count. The suite drives two browsers, a
  claimant and an officer, through the full stack.
- **Cost:** about 20 seconds, and the stack must be up. Each test writes its own policy straight to
  PostgreSQL so tests never collide.

## What I would do with more time

In rough order of value:

1. **CI.** Run `./mvnw verify` and the Playwright suite on every push, with Docker Compose in the
   pipeline. Today the tests run locally.
2. **Identity.** An identity provider (Keycloak or the insurer's own) with asymmetric keys, refresh
   tokens, sign-in rate limiting and MFA for staff.
3. **Ready to scale out.** Documents in S3 behind the existing storage class, change data capture
   in place of the polling relay, and a load test with several instances of each service.
4. **Operations.** An admin view to inspect and replay dead letters; retention jobs for published
   outbox rows and read notifications; tracing across REST and Kafka keyed by claim id; alerts on
   outbox and consumer lag.
5. **Rules designed but not built.** Manager sign-off above an authority limit, claimant
   withdrawal, payment as its own service with a bank, several payments per claim, reserves based
   on average claim cost by market and peril, and personalised (vanity) plates.
6. **Frontend.** An accessibility check (axe) inside the Playwright suite, translations for each
   market, unit tests for the validators, and an e2e clean-up of its own data.
7. **An API gateway** in production for TLS, rate limits and one origin; locally nginx does the
   routing.

## Tests

| Module | Tests | Covers |
|---|---|---|
| `claim-events` | 10 | JSON round trip of every event type, unknown fields ignored |
| `claims-service` | 105 | sign-in, roles, every state transition, concurrency, field validation and clean-up, documents, payment, follow-ups, the cached lists and queue (including Redis down), policy detail, outbox relay incl. a broker outage |
| `reporting-service` | 27 | projection, duplicate and stale events, dead-lettering, the three reports and their cache (including Redis down) |
| `notification-service` | 27 | event-to-notification rules, inbox, mark all read, live stream across instances (including Redis down), duplicate delivery |

| `frontend` (Playwright, end to end) | 15 | both sign-in doors, route guards, password toggle, policy list and detail, every claim form rule, draft restore, a motor claim from submission through a question and reply to settlement, a property claim rejected with a reason, live badges and bell |

The Playwright suite runs against the Docker stack. With the stack up:

```bash
cd frontend && npx playwright install chromium && npm run e2e
```

It signs in as its own claimant (`e2e.claimant@claims.test`, created on first run from the seeded
password) and gives each test a fresh policy written straight to PostgreSQL on port 5433, so runs
never depend on each other or on the demo data. `E2E_BASE_URL` and `E2E_DATABASE_URL` override the
defaults.

## Seeded accounts (local demo data only)

Every account uses the password `password`. Demo data only: the seed lives in `db/seed` and must not be loaded anywhere real.

| Email | Role | Market | Sign in at |
|---|---|---|---|
| `aisha@claims.test` | Claimant (motor + property policy) | MY | `/login` |
| `daniel@claims.test` | Claimant (motor policy) | MY | `/login` |
| `siti@claims.test` | Claimant (motor policy) | SG | `/login` |
| `officer1.my@claims.test` | Officer | MY | `/admin/login` |
| `officer2.my@claims.test` | Officer | MY | `/admin/login` |
| `officer.sg@claims.test` | Officer | SG | `/admin/login` |
| `manager.my@claims.test` | Manager | MY | `/admin/login` |
