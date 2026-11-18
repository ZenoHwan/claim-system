# Claims Platform

Motor and property claims platform: claimants report and track claims, staff
pick them up and take them to settlement or rejection, managers see workload
and outstanding exposure.

- Design decisions: [`openspec/changes/claims-platform-mvp/design.md`](openspec/changes/claims-platform-mvp/design.md)
- Diagrams: [`docs/`](docs)
- How the AI was directed: [`AI_JOURNAL.md`](AI_JOURNAL.md)

## Layout

| Path | What |
|---|---|
| `claim-events/` | Event records shared by producer and consumers |
| `claims-service/` | System of record: sign-in, claims, documents, payment, outbox relay (port 8080) |
| `reporting-service/` | Workload, performance and exposure read model (port 8081) |
| `notification-service/` | Inbox and live push (port 8082) |
| `frontend/` | Angular app (port 4200) |
| `docker-compose.yml` | PostgreSQL and Kafka |

## Prerequisites

- JDK 21 (`brew install openjdk@21`) with `JAVA_HOME` pointing at it:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Docker
- Node 22 (frontend only)

## Start

1. **Infrastructure.** PostgreSQL on host port **5433** (databases `claims_db`,
   `reporting_db`, `notification_db`) and Kafka on **9092**:

   ```bash
   docker compose up -d
   ```

   Wait until `docker compose ps` shows both as `healthy`.

2. **Build and test** (integration tests start their own PostgreSQL and Kafka
   containers, so Docker must be running):

   ```bash
   ./mvnw verify
   ```

3. **Run the three services**, each in its own terminal:

   ```bash
   java -jar claims-service/target/claims-service-0.0.1-SNAPSHOT.jar
   ```

   ```bash
   java -jar reporting-service/target/reporting-service-0.0.1-SNAPSHOT.jar
   ```

   ```bash
   java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar
   ```

4. **Run the frontend** (Node 22):

   ```bash
   cd frontend && npm install --legacy-peer-deps && npm start
   ```

   Open http://localhost:4200/login (claimants) or
   http://localhost:4200/admin/login (staff).

To wipe local data: `docker compose down -v` and delete `data/`.

## A five-minute walkthrough

Use two browser windows (one normal, one private) so both sessions stay signed in.

1. Claimant `aisha@claims.test` at `/login`: **New claim**, pick the motor policy, attach a photo, submit.
2. Officer `officer1.my@claims.test` at `/admin/login`: **Queue**, **Pick up**, then **Request information**.
   The claimant's bell updates without a refresh.
3. Claimant: reply with a PDF. The officer's bell updates.
4. Officer: **Assess**, **Approve**, **Record payment**. The claimant sees each step live.
5. Manager `manager.my@claims.test`: **Dashboard**. Exposure rises on submission, changes on
   assessment and drops on settlement.
6. Resilience: `docker compose stop kafka`, submit another claim (it is accepted),
   `docker compose start kafka`, and watch the dashboard catch up.

## What is built and what is not

Built: sign-in through two doors, the full claim lifecycle from submission to settlement or
rejection, information requests, document upload, a thin payment record, event publishing through
a transactional outbox, workload / performance / exposure reporting, and live in-app notifications.

Designed only (see `docs/`): manager sign-off above an authority limit, claimant withdrawal,
payment execution through a bank, multiple payments per claim, currency conversion, real email,
an API gateway, an identity provider. Known shortcuts are marked in the code with `ponytail:`.

## Tests

| Module | Tests | Covers |
|---|---|---|
| `claim-events` | 10 | JSON round trip of every event type, unknown fields ignored |
| `claims-service` | 83 | sign-in, roles, every state transition, concurrency, documents, payment, outbox relay incl. a broker outage |
| `reporting-service` | 25 | projection, duplicate and stale events, dead-lettering, the three reports |
| `notification-service` | 24 | event-to-notification rules, inbox, live stream, duplicate delivery |

The frontend has no automated tests; it was checked by hand in the browser.

## Seeded accounts (local demo data only)

Every account uses the password `Passw0rd!`.

| Email | Role | Market | Sign in at |
|---|---|---|---|
| `aisha@claims.test` | Claimant (motor + property policy) | MY | `/login` |
| `daniel@claims.test` | Claimant (motor policy) | MY | `/login` |
| `siti@claims.test` | Claimant (motor policy) | SG | `/login` |
| `officer1.my@claims.test` | Officer | MY | `/admin/login` |
| `officer2.my@claims.test` | Officer | MY | `/admin/login` |
| `officer.sg@claims.test` | Officer | SG | `/admin/login` |
| `manager.my@claims.test` | Manager | MY | `/admin/login` |
