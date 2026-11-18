# Tasks

Working rules for every group: write the failing test first for anything with logic, then the code, then refactor (design D17). One Conventional Commit per green cycle. Add an `AI_JOURNAL.md` entry whenever an AI suggestion is accepted, challenged or overridden.

## 1. Repository and local environment

- [x] 1.1 Install JDK 21 and add the Maven wrapper; verify `./mvnw -v` reports Java 21
- [x] 1.2 Create the parent POM with modules `claim-events`, `claims-service`, `reporting-service`, `notification-service`; verify `./mvnw -q verify` succeeds on empty modules
- [x] 1.3 Add `docker-compose.yml` with PostgreSQL (databases `claims_db`, `reporting_db`, `notification_db`) and Kafka, each with a health check; verify `docker compose up -d` reaches healthy and all three databases accept a connection
- [x] 1.4 Add a README with prerequisites and start order; verify a clean checkout can follow it to a running compose stack

## 2. Event contract (`claim-events`)

- [x] 2.1 Define the event envelope and claim snapshot records and the eight event types; verify a unit test round-trips each event type through JSON unchanged
- [x] 2.2 Verify by test that deserialising an event with an unknown extra field succeeds

## 3. claims-service: foundation and identity

- [x] 3.1 Scaffold the Spring Boot app with Flyway and the migration for `team`, `app_user`, `policy`; verify the app starts against compose and the tables exist
- [x] 3.2 Seed teams, one manager, officers, and claimants with a motor and a property policy (BCrypt passwords); verify by integration test that the seeded accounts exist after a fresh start
- [x] 3.3 Test-first: sign-in issues a token for the matching door and refuses the wrong door and a wrong password; verify the identity-access sign-in scenarios pass
- [x] 3.4 Test-first: protected endpoints return 401 without a valid token and 403 for the wrong role; verify the authentication and role scenarios pass
- [x] 3.5 Add problem-details error handling for validation, not-found, forbidden and conflict; verify by test that a validation failure returns 400 with per-field messages

## 4. claims-service: claim lifecycle

- [x] 4.1 Add migrations for `claim`, `motor_claim_detail`, `property_claim_detail`, `claim_status_history`, `reserve`, `assessment`, `decision`, `outbox_event`; verify the app starts and the schema matches `docs/er-diagram.md`
- [x] 4.2 Test-first: the claim state machine accepts each permitted transition and refuses every other; verify unit tests cover all status pairs
- [x] 4.3 Test-first: submit a claim (ownership, policy period, field validation, line-specific details, claim number, initial reserve); verify the submission scenarios in claim-lifecycle pass
- [x] 4.4 Test-first: claimant lists their claims and opens one with its timeline, and cannot open another claimant's; verify the tracking scenarios pass
- [x] 4.5 Test-first: unassigned queue by market and pick-up, including a concurrent pick-up where exactly one caller wins and the other gets 409; verify the pick-up scenarios pass
- [x] 4.6 Test-first: assess (assigned staff only, one assessment, reserve revised); verify the assessment scenarios pass
- [x] 4.7 Test-first: approve and reject (amount within sum insured, reason required, reserve released on rejection); verify the decision scenarios pass
- [x] 4.8 Test-first: each transition writes a history row and an outbox row in the same transaction, and a refused action writes neither; verify by integration test against PostgreSQL
- [x] 4.9 Test-first: a manager can perform every staff action; verify the manager scenario in identity-access passes

## 5. claims-service: information requests and documents

- [x] 5.1 Add migrations for `information_request` and `claim_document`; verify the app starts and the tables exist
- [x] 5.2 Test-first: request information and respond, including repeat requests and the conflict cases; verify the information-requests scenarios pass
- [x] 5.3 Test-first: upload with type-by-content and size checks, link to an information request, and refuse closed claims and staff uploads; verify the upload scenarios in claim-documents pass
- [x] 5.4 Test-first: list and download with per-claim authorisation; verify the download scenarios pass and that a path-traversal file name cannot escape the storage directory

## 6. claims-service: settlement

- [x] 6.1 Add the `payment` migration with a unique constraint on `claim_id`; verify by test that inserting a second payment for a claim fails at the database
- [x] 6.2 Test-first: approval raises a REQUESTED payment and rejection raises none; verify the payment-raised scenarios pass
- [x] 6.3 Test-first: recording the payment marks it PAID, settles the claim and releases the reserve, with the conflict and validation cases; verify the claim-settlement scenarios pass

## 7. claims-service: event publishing

- [x] 7.1 Test-first: the outbox relay publishes unpublished rows oldest first, keyed by claim id, and stamps `published_at`; verify with a Testcontainers Kafka test that a full lifecycle produces the expected events in order
- [x] 7.2 Test-first: with the broker unavailable the claim action still succeeds and the row stays unpublished, then is published when the broker returns; verify by integration test
- [x] 7.3 Publish the OpenAPI document for claims-service; verify `/v3/api-docs` lists every endpoint used above

## 8. reporting-service

- [x] 8.1 Scaffold the app with the `claim_summary` and `processed_event` migrations and token validation; verify it starts against compose and rejects an unauthenticated request with 401
- [x] 8.2 Test-first: consuming each event type upserts `claim_summary`; verify with Testcontainers that a full lifecycle leaves the expected row
- [x] 8.3 Test-first: a duplicate event and an older-version event change nothing; verify the replay scenarios in claims-reporting and claim-events pass
- [x] 8.4 Configure retry with backoff and the dead-letter topic; verify by test that an unprocessable message lands on `claim-events.dlt` and the next message is processed
- [x] 8.5 Test-first: workload query (per officer by status, unassigned, ageing buckets); verify the workload scenarios pass
- [x] 8.6 Test-first: performance query (decided in 30 days, average days to decision, empty case); verify the performance scenarios pass
- [x] 8.7 Test-first: exposure query by market and line with the as-of time, manager-only; verify the exposure scenarios pass and an officer gets 403
- [x] 8.8 Publish the OpenAPI document for reporting-service; verify `/v3/api-docs` lists the three views

## 9. notification-service

- [x] 9.1 Scaffold the app with the `notification` and `processed_event` migrations and token validation; verify it starts and rejects an unauthenticated request with 401
- [x] 9.2 Test-first: map events to recipients and messages (claimant on pick-up, information requested, approved, rejected, settled; assigned staff on response; none on submission); verify the notification scenarios pass
- [x] 9.3 Test-first: a duplicate event creates one notification; verify the one-per-event scenario passes
- [x] 9.4 Test-first: inbox list, unread count and mark-as-read scoped to the signed-in user; verify the inbox scenarios pass
- [x] 9.5 Add the SSE stream and the logged email stub; verify by test that a subscribed user receives a notification created after subscribing and that a log line records the stubbed email
- [x] 9.6 Publish the OpenAPI document for notification-service; verify `/v3/api-docs` lists the inbox and stream endpoints

## 10. Angular application

- [x] 10.1 Scaffold the Angular app with Angular Material, the dev proxy to the three services, and API clients generated from the three OpenAPI documents; verify `ng build` succeeds
- [x] 10.2 Build `/login` and `/admin/login`, token storage, an HTTP interceptor and role guards for `/claims/**` and `/admin/**`; verify in the browser that each seeded role lands in its own area and is refused at the other door
- [x] 10.3 Build the claimant claims list and the new-claim stepper (policy, incident, line-specific details, documents, review); verify a claim can be submitted with a photo and appears in the list
- [x] 10.4 Build the claimant claim detail (timeline, information-request reply with upload, decision and payment status); verify a reply returns the claim to under review
- [x] 10.5 Build the staff queue, my-claims list and claim workbench (request information, assess, approve or reject, record payment, document download); verify a claim can be taken from pick-up to settled, and that a lost pick-up race shows a clear message
- [x] 10.6 Build the notification bell with the SSE stream; verify a claimant with the page open sees the badge change when an officer requests information
- [x] 10.7 Build the manager dashboard (workload, performance, exposure, as-of time); verify the figures change after a claim is assessed and settled

## 11. End-to-end check

- [x] 11.1 From a clean checkout, follow the README to start everything and walk one motor and one property claim through the full lifecycle with two browser sessions; verify notifications arrive live and exposure moves at each step
- [x] 11.2 Stop Kafka, submit a claim, restart Kafka; verify the claim was accepted and the dashboard and notifications catch up
- [x] 11.3 Run `./mvnw verify` for all modules; verify every test passes
- [ ] 11.4 Review `AI_JOURNAL.md` and fill in every outstanding "Why"; verify no placeholder remains

## 12. Changes after first hands-on test

- [x] 12.1 Test-first: the claimed amount is optional and a missing one gives a default reserve as a share of the sum insured; verify the estimate and default-reserve scenarios pass
- [x] 12.2 Test-first: only one open claim per policy, enforced in the service and by a partial unique index, and shown on the policy list; verify the one-open-claim scenarios pass
- [x] 12.3 New-claim form: Material calendar on the incident date, optional estimate, policies with a claim in progress disabled; verify each in the browser
- [x] 12.4 New-claim form keeps its values and step across a page refresh; verify by refreshing on step 2 and landing back on step 2 with the values

## Workflow follow-up

- Archive the change once the walkthrough has been given.
