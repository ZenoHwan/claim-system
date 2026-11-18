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

1. Infrastructure: PostgreSQL on host port **5433** (databases `claims_db`,
   `reporting_db`, `notification_db`) and Kafka on **9092**.

   ```bash
   docker compose up -d
   ```

   Wait until `docker compose ps` shows both as `healthy`.

2. Build and test everything (integration tests start their own containers):

   ```bash
   ./mvnw verify
   ```

To wipe local data: `docker compose down -v`.

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
