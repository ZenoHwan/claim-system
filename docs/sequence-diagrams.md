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
    CS->>CS: Validate policy ownership and incident date
    rect rgb(235, 245, 255)
        note over CS,DB: one database transaction
        CS->>DB: insert claim (SUBMITTED) + detail row
        CS->>DB: insert reserve (INITIAL = claimed amount)
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
    UI->>CS: POST /api/claims/{id}/assign (version)
    alt version still current
        CS-->>UI: 200 OK (UNDER_REVIEW)
    else another officer got there first
        CS-->>UI: 409 Conflict
        UI-->>O: "Already picked up" and refresh queue
    end

    O->>UI: Ask for police report
    UI->>CS: POST /api/claims/{id}/information-requests
    CS-->>UI: 201 Created (INFO_REQUESTED)
    CS->>K: InformationRequested (via outbox)
    K->>NS: InformationRequested
    NS->>NS: insert notification for claimant
    NS-->>UI: SSE push to claimant's open session
    UI-->>C: Bell badge: "More information needed"

    C->>UI: Open claim, type reply, attach PDF
    UI->>CS: POST /api/information-requests/{id}/response
    CS-->>UI: 200 OK (UNDER_REVIEW)
    CS->>K: InformationProvided (via outbox)
    K->>NS: InformationProvided
    NS-->>UI: SSE push to officer
    UI-->>O: Bell badge: "Claimant responded"
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
    UI->>CS: POST /api/claims/{id}/assessment
    CS->>CS: insert assessment + new reserve row
    CS->>K: ClaimAssessed (reserve = recommended amount)
    K->>RS: ClaimAssessed
    RS->>RS: claim_summary.current_reserve updated

    O->>UI: Approve
    UI->>CS: POST /api/claims/{id}/decision (APPROVED, amount)
    CS->>CS: insert decision + payment (REQUESTED)
    CS->>K: ClaimApproved

    O->>UI: Record payment (method, reference)
    UI->>CS: POST /api/claims/{id}/payment/confirm
    CS->>CS: payment PAID, claim SETTLED, reserve released
    CS->>K: ClaimSettled
    K->>RS: ClaimSettled
    RS->>RS: current_reserve = 0, settled_at set

    M->>UI: Open dashboard
    UI->>RS: GET /api/reports/exposure
    RS-->>UI: reserve by market and line (as of last event)
```

## 4. Future: payment as its own service

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
