# Proposal

## Why

Motor and property claims across six APAC markets are handled by phone, email, shared inboxes and spreadsheets. Claimants cannot see what is happening to their claim, claims staff have no consolidated view of their work, and managers have no live picture of outstanding claims or liability exposure. There is no existing system; this change builds the first working slice of the platform.

## What Changes

- Claimants can sign in, report an incident against one of their policies, attach evidence, track the claim on a timeline, answer requests for more information, and see the decision and payment status.
- Claims staff can sign in through a separate staff door, pick up unassigned claims in their market, request information, assess, approve or reject, and record the settlement payment.
- Managers can do everything an officer can and additionally see team workload, performance and outstanding exposure by market and line of business.
- Every claim state change is published as an event so that reporting and notifications are built without slowing or blocking claim handling.
- Claimants and staff receive in-app notifications, pushed live, when a claim they are involved in changes.

Out of scope for this change (designed in `docs/`, not built): manager sign-off above an authority limit, claimant withdrawal, payment execution through a bank, multiple payments per claim, currency conversion, real email delivery, an API gateway, and an external identity provider.

## Capabilities

### New Capabilities

- `identity-access`: Sign-in for claimants and staff through separate doors, roles, and what each role may reach.
- `claim-lifecycle`: Submitting a claim against a policy, picking it up, assessing it, deciding it, the permitted state transitions, reserve tracking and the status history.
- `information-requests`: Staff asking a claimant for more information and the claimant responding.
- `claim-documents`: Uploading, listing and downloading evidence files on a claim.
- `claim-settlement`: The payment raised when a claim is approved and how recording it settles the claim.
- `claim-events`: The events published for claim state changes and the delivery guarantees downstream consumers can rely on.
- `claims-reporting`: Workload, performance and liability exposure figures for staff and managers.
- `claim-notifications`: In-app notifications to claimants and staff triggered by claim events.

### Modified Capabilities

None. This is the first change in the project.

## Impact

- **New code:** three backend services (claims, reporting, notification), a shared event contract module, and one Angular application with claimant and staff areas.
- **APIs:** REST endpoints per service, each publishing its own OpenAPI document; one server-sent-events stream for live notifications.
- **Messaging:** one Kafka topic for claim events plus a dead-letter topic.
- **Data:** three PostgreSQL databases, one per service, with versioned migrations and seeded reference data (users, teams, policies).
- **Local runtime:** Docker Compose for PostgreSQL and Kafka; a local folder for uploaded documents.
- **Documentation:** architecture, data model, sequence, state and user-flow diagrams in `docs/`; a running `AI_JOURNAL.md`.
