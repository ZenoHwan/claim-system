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

`docker compose ps` should list six containers: `postgres`, `kafka`, `claims-service`,
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

After changing code, rebuild just that part, for example `docker compose up -d --build claims-service`.
To wipe all data: `docker compose down -v`.

### On the host (faster while changing code)

Needs JDK 21 and Node 22 as well. Stop the app containers first so the ports are free.

1. Infrastructure only:

   ```bash
   docker compose up -d postgres kafka
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
