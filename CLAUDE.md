# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Motor and property claims platform: a multi-module Maven build (Java 21, Spring Boot 4, Jackson 3 `tools.jackson`) with three services, a shared events module, and an Angular 22 frontend. The design record is `openspec/changes/archive/2026-11-25-claims-platform-mvp/design.md` (decisions D1–D21); the current requirements are the main specs in `openspec/specs/`. The README has the run instructions, the trade-offs and the seeded accounts.

## Commands

Backend (needs Docker running: every integration test starts PostgreSQL, Kafka and Redis with Testcontainers):

```bash
./mvnw verify                                   # build and test all modules
./mvnw -pl claims-service -am verify            # one service plus what it depends on (claim-events)
./mvnw -pl claims-service -am test -Dtest=FollowUpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Integration tests are plain Surefire tests named `*IntegrationTest` that extend a per-module `IntegrationTest` / `IntegrationTestBase`. There is no Failsafe and no linter.

Whole stack in Docker (frontend on http://localhost:4200, PostgreSQL on host port **5433**):

```bash
docker compose up -d --build                    # everything
docker compose up -d --build claims-service     # rebuild one service after a change
docker compose up -d postgres kafka redis       # infrastructure only, to run services on the host
docker compose down -v                          # wipe all data
```

Frontend (`cd frontend`; install with `npm install --legacy-peer-deps`):

```bash
npm start                                       # ng serve; proxy.conf.json routes /api/* to 8080/8081/8082
npm run build
npm run e2e                                     # Playwright against the running Docker stack
npx playwright test e2e/lifecycle.spec.ts -g "property claim"
npm run api:fetch && npm run api:generate       # refresh src/app/api/generated/* from the running services' OpenAPI
```

There are no Angular unit tests. Frontend behaviour is covered by the Playwright suite in `frontend/e2e`, which runs against the real stack. It creates its own claimant (`e2e.claimant@claims.test`) and a fresh policy per test, written straight to PostgreSQL on 5433. `E2E_BASE_URL` and `E2E_DATABASE_URL` override the defaults.

## Architecture

**Services and data.** Each service owns its own database on one PostgreSQL server (`infra/init-databases.sql`), migrated by Flyway:
- `claims-service` (8080) is the system of record: sign-in, policies, claims, documents and payment.
- `reporting-service` (8081) keeps a read model with one row per claim and aggregates on read.
- `notification-service` (8082) holds the inbox and the live push.
- `claim-events` holds the event records shared by producer and consumers.

Demo data is a second Flyway location, `claims-service/src/main/resources/db/seed`, enabled in `application.yml`. Every seeded account's password is `password`.

**Write path: one door for every state change.** Every claim state change goes through `ClaimChanges.record(...)` inside the caller's transaction. It saves the claim, a `claim_status_history` row and an `outbox_event` row together. After commit, it evicts the Redis-cached lists that show the claim. Legal transitions live in `ClaimStatus.next()` / `canMoveTo`. Concurrent edits are caught by optimistic locking on the claim's `version` and come back to the client as 409.

**Events.** `OutboxRelay` polls unpublished `outbox_event` rows every second and sends them to the `claim-events` topic, keyed by claim id (3 partitions, so each claim's events stay in order). It stamps `published_at` only after the broker acks. It stops at the first failure, so a later event never overtakes an earlier one. Delivery is at least once. Consumers in reporting and notification dedupe through their own `processed_event` table (`INSERT … ON CONFLICT DO NOTHING`). A failing event is retried with backoff, then parked on `claim-events.dlt`. Events are full JSON snapshots of the claim, not deltas.

**Auth.** `claims-service` issues the JWT. All three services verify it with the same HMAC secret (`claims.jwt.secret`, overridable with `CLAIMS_JWT_SECRET`). There are two sign-in doors: `/login` for claimants, leading to `/claims/**`, and `/admin/login` for staff, leading to `/admin/**`. The server rejects a valid account at the wrong door. Officers and managers are scoped to a market.

**Redis.** Redis has two jobs, and every service still starts and serves from the database when Redis is down (the timeouts are 500ms, and `LoggingCacheErrorHandler` logs and ignores cache errors):
- **Cache.** `@Cacheable` caches with a 60s TTL as a backstop:
  - claims-service: Follow-ups, each claimant's and officer's claim list, each market's queue (`claim/CacheConfig`);
  - reporting-service: workload, performance, exposure.
  - Entries are evicted after commit, by `ClaimChanges` and by `ClaimSummaryProjector`.
  - Claim detail is deliberately not cached.
- **Live push.** Kafka gives each event to one notification-service instance, but the user's SSE connection may be held by another. `LiveBroadcast` therefore publishes each stored notification on the Redis channel `claim-notifications`, and every instance pushes it to its own connections for `recipientId` (`LiveStream`). If Redis is down, it pushes locally only. It subscribes after startup, with a retry.

**Frontend.** One Angular app with standalone components, signals and Angular Material:
- Areas: `claimant/` and `staff/` are lazy-loaded; shared services are in `core/`.
- API types are generated from each service's OpenAPI into `src/app/api/generated`, so a backend contract change needs `api:fetch` + `api:generate`.
- The live stream is opened with `fetch` (to send the Authorization header) in `core/notifications.ts`. Claim screens reload when `lastPushed()` matches their claim.
- In Docker, `frontend/nginx.conf` serves the build and proxies `/api/*`. It uses Docker's resolver with variable `proxy_pass` so that recreated containers are re-resolved.

**Images.** One `Dockerfile` builds all three services (`--build-arg MODULE=<service>`). It skips tests; tests run on the host.

## Conventions

- Conventional Commits with scopes `claims`, `reporting`, `notification`, `events`, `frontend`, `docs`.
- A design change is reflected in several places, which are kept in step:
  - `design.md` (the D-numbered decision);
  - the README's "Trade-offs: why not X?" and test-count table;
  - the diagrams in `docs/` (C4, sequence, ER, state machine);
  - a new numbered entry in `AI_JOURNAL.md`.
- `ponytail:` comments mark deliberate simplifications and name the upgrade path. Keep them accurate when changing that code.
- Server-side validation is the source of truth; the Angular form mirrors the same rules.
