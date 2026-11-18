# Sequence Diagrams

## 1. Submit a claim (REST command, outbox, Kafka fan-out)

The pattern every state change follows.

```mermaid
sequenceDiagram
    autonumber
    actor C as Claimant
    participant UI as Angular
    participant CS as claims-service
    participant DB as claims_db
    participant K as Kafka claim-events
    participant RS as reporting-service
    participant NS as notification-service

    C->>UI: Fill in new-claim stepper, attach photos
    UI->>CS: POST /api/claims (JWT)
    CS->>CS: Trim and normalise text, validate every field,<br/>policy ownership, cover dates, postal code for the market
    rect rgb(235, 245, 255)
        note over CS,DB: one database transaction
        CS->>DB: insert claim (SUBMITTED) + detail row
        CS->>DB: insert reserve (INITIAL = claimed amount,<br/>or a share of the sum insured)
        CS->>DB: insert claim_status_history
        CS->>DB: insert outbox_event (ClaimSubmitted)
    end
    CS-->>UI: 201 Created (claim number)
    UI->>CS: POST /api/claims/{id}/documents (multipart)
    CS-->>UI: 201 Created
    UI-->>C: "Claim CLM-2026-000123 submitted"

    loop every second
        CS->>DB: select unpublished outbox rows (skip locked)
        CS->>K: publish ClaimSubmitted (key = claim id)
        CS->>DB: set published_at
    end

    par reporting
        K->>RS: ClaimSubmitted
        RS->>RS: skip if event id seen or version is older
        RS->>RS: upsert claim_summary
    and notification
        K->>NS: ClaimSubmitted
        NS->>NS: skip if event id seen
        NS->>NS: no claimant message for this event
    end
```

If the service crashes between publishing and setting `published_at`, the event
is published again on restart. Consumers cope because they record each event id
in `processed_event`.

## 2. Pick up, request information, respond (async across both tiers)

The Follow-ups lists are not stored: `claims-service` works them out from each claim's status and
its latest information request, so they clear themselves when the claim moves on.

```mermaid
sequenceDiagram
    autonumber
    actor O as Officer
    actor C as Claimant
    participant UI as Angular
    participant CS as claims-service
    participant K as Kafka claim-events
    participant NS as notification-service

    O->>UI: Click "Pick up" in unassigned queue
    UI->>CS: POST /api/staff/claims/{id}/assign
    alt still unassigned (version column unchanged)
        CS-->>UI: 200 OK (UNDER_REVIEW)
    else another officer got there first
        CS-->>UI: 409 Conflict
        UI-->>O: "Already picked up" and refresh queue
    end

    O->>UI: Ask for police report
    UI->>CS: POST /api/staff/claims/{id}/information-requests
    CS-->>UI: 200 OK (INFO_REQUESTED)
    CS->>K: InformationRequested (via outbox)
    K->>NS: InformationRequested
    NS->>NS: insert notification for claimant
    NS-->>UI: SSE push to claimant's open session
    UI-->>C: Bell badge: "More information needed"
    UI->>CS: GET /api/claims/follow-ups
    CS-->>UI: questions waiting for a reply
    UI-->>C: Follow-ups tab count goes up
    C->>UI: Open the bell
    UI->>NS: POST /api/notifications/read-all
    NS-->>UI: 204 (unread count cleared)

    C->>UI: Open Follow-ups, Reply, type reply, attach PDF
    UI->>CS: POST /api/claims/{id}/documents (each file, linked to the request)
    UI->>CS: POST /api/claims/{id}/information-requests/{requestId}/response
    CS-->>UI: 200 OK (UNDER_REVIEW)
    UI->>CS: GET /api/claims/follow-ups
    UI-->>C: Follow-ups count goes down
    CS->>K: InformationProvided (via outbox)
    K->>NS: InformationProvided
    NS-->>UI: SSE push to officer
    UI-->>O: Bell badge: "Claimant responded"
    UI->>CS: GET /api/staff/claims/follow-ups
    UI-->>O: Follow-ups tab counts one reply to review
```

## 3. Assess, decide, settle (exposure follows the reserve)

```mermaid
sequenceDiagram
    autonumber
    actor O as Officer
    actor M as Manager
    participant UI as Angular
    participant CS as claims-service
    participant K as Kafka claim-events
    participant RS as reporting-service

    O->>UI: Enter findings and recommended amount
    UI->>CS: POST /api/staff/claims/{id}/assessment
    CS->>CS: insert assessment + new reserve row
    CS->>K: ClaimAssessed (reserve = recommended amount)
    K->>RS: ClaimAssessed
    RS->>RS: claim_summary.current_reserve updated

    O->>UI: Approve
    UI->>CS: POST /api/staff/claims/{id}/decision (APPROVED, amount)
    CS->>CS: insert decision + payment (REQUESTED)
    CS->>K: ClaimApproved

    O->>UI: Record payment (method, reference)
    UI->>CS: POST /api/staff/claims/{id}/payment
    CS->>CS: payment PAID, claim SETTLED, reserve released
    CS->>K: ClaimSettled
    K->>RS: ClaimSettled
    RS->>RS: current_reserve = 0, settled_at set

    M->>UI: Open dashboard
    UI->>RS: GET /api/reports/exposure
    RS-->>UI: reserve by market and line (as of last event)
```

## 4. Cached reads: Follow-ups and dashboard reports

The two reads that grow with usage go through Redis. A change clears the affected entries after it
commits, so the next read is rebuilt from PostgreSQL; the 60-second expiry is only a backstop. If
Redis does not answer within 500 ms, the error is logged and PostgreSQL answers.

```mermaid
sequenceDiagram
    autonumber
    actor U as Claimant or officer
    participant UI as Angular
    participant CS as claims-service
    participant R as Redis
    participant DB as claims_db

    U->>UI: Move to another page
    UI->>CS: GET /api/claims/follow-ups
    CS->>R: GET followUps::{user id}
    alt cached
        R-->>CS: list (JSON)
    else not cached, or Redis down
        CS->>DB: claims and their latest question
        CS->>R: SET followUps::{user id}, 60 s expiry
    end
    CS-->>UI: Follow-ups list and count

    U->>UI: Reply, assess, approve...
    UI->>CS: POST (any claim change)
    CS->>DB: claim, history, outbox (one transaction)
    CS->>R: after commit: DEL followUps::{claimant}, followUps::{officer}

    note over CS,R: reporting-service does the same for its reports:<br/>each applied event clears workload::{market},<br/>performance::{market} and exposure::all
```

## 5. Future: payment as its own service

Not built. Shows where the thin `payment` table goes next.

```mermaid
sequenceDiagram
    autonumber
    participant CS as claims-service
    participant K as Kafka
    participant PS as payment-service
    participant B as Bank

    CS->>K: PaymentRequested (idempotency key = payment id)
    K->>PS: PaymentRequested
    PS->>B: Submit transfer
    alt success
        B-->>PS: Confirmed
        PS->>K: PaymentCompleted
        K->>CS: PaymentCompleted
        CS->>CS: payment PAID, claim SETTLED
    else failure
        B-->>PS: Rejected
        PS->>K: PaymentFailed
        K->>CS: PaymentFailed
        CS->>CS: payment FAILED, officer notified
    end
```
