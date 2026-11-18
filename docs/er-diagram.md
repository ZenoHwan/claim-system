# Entity-Relationship Diagram

Three databases, one per service. No foreign keys cross a database boundary:
`reporting_db` and `notification_db` are built only from Kafka events.

## claims_db (system of record, owned by claims-service)

```mermaid
erDiagram
    team ||--o{ app_user : "has members"
    app_user ||--o{ policy : "holds"
    policy ||--o{ claim : "is claimed against"
    app_user ||--o{ claim : "submits (claimant)"
    app_user |o--o{ claim : "is assigned (officer)"
    claim ||--o| motor_claim_detail : "motor only"
    claim ||--o| property_claim_detail : "property only"
    claim ||--|{ claim_status_history : "records"
    claim ||--o{ information_request : "has"
    claim ||--o{ claim_document : "has"
    information_request |o--o{ claim_document : "answered with"
    claim ||--o| assessment : "has"
    claim ||--|{ reserve : "has history of"
    claim ||--o| decision : "ends in"
    claim ||--o| payment : "is paid by"

    team {
        uuid id PK
        varchar name
        varchar market_code "SG, MY, HK, AU, TH, ID"
    }
    app_user {
        uuid id PK
        varchar email UK
        varchar full_name
        varchar password_hash
        varchar role "CLAIMANT, OFFICER, MANAGER"
        varchar market_code
        uuid team_id FK "null for claimants"
    }
    policy {
        uuid id PK
        varchar policy_number UK
        uuid holder_id FK
        varchar line_of_business "MOTOR, PROPERTY"
        varchar market_code
        varchar currency
        numeric sum_insured
        date effective_from
        date effective_to
    }
    claim {
        uuid id PK
        varchar claim_number UK
        uuid policy_id FK
        uuid claimant_id FK
        uuid assigned_officer_id FK "null until picked up"
        varchar line_of_business "copied from policy"
        varchar market_code "copied from policy"
        varchar currency "copied from policy"
        varchar status
        date incident_date
        text incident_description
        numeric claimed_amount
        bigint version "optimistic lock"
        timestamptz submitted_at
        timestamptz updated_at
    }
    motor_claim_detail {
        uuid claim_id PK, FK
        varchar vehicle_registration
        varchar driver_name
        varchar accident_location
        boolean third_party_involved
    }
    property_claim_detail {
        uuid claim_id PK, FK
        varchar property_address
        varchar loss_cause "FIRE, WATER, THEFT, STORM, OTHER"
    }
    claim_status_history {
        uuid id PK
        uuid claim_id FK
        varchar from_status "null on submission"
        varchar to_status
        uuid actor_id FK
        text reason
        timestamptz changed_at
    }
    information_request {
        uuid id PK
        uuid claim_id FK
        uuid requested_by FK
        text question
        varchar status "OPEN, ANSWERED"
        text response_text
        timestamptz requested_at
        timestamptz answered_at
    }
    claim_document {
        uuid id PK
        uuid claim_id FK
        uuid information_request_id FK "null if attached at submission"
        uuid uploaded_by FK
        varchar file_name
        varchar content_type "pdf, jpeg, png"
        bigint size_bytes "max 10 MB"
        varchar storage_key "path in document volume"
        timestamptz uploaded_at
    }
    assessment {
        uuid id PK
        uuid claim_id FK, UK
        uuid assessed_by FK
        text findings
        numeric recommended_amount
        timestamptz assessed_at
    }
    reserve {
        uuid id PK
        uuid claim_id FK
        numeric amount
        varchar reason "INITIAL, ASSESSMENT, RELEASED"
        uuid set_by FK "null for INITIAL"
        timestamptz created_at "latest row is current"
    }
    decision {
        uuid id PK
        uuid claim_id FK, UK
        varchar outcome "APPROVED, REJECTED"
        numeric approved_amount "null if rejected"
        text reason
        uuid decided_by FK
        timestamptz decided_at
    }
    payment {
        uuid id PK
        uuid claim_id FK, UK "unique = double-payment guard"
        varchar payee_name
        varchar method "BANK_TRANSFER, CHEQUE"
        numeric amount
        varchar currency
        varchar status "REQUESTED, PAID"
        varchar reference
        timestamptz requested_at
        timestamptz paid_at
    }
    outbox_event {
        uuid id PK "also the event id"
        uuid aggregate_id "claim id = Kafka key"
        varchar event_type
        jsonb payload
        timestamptz created_at
        timestamptz published_at "null until relayed"
    }
```

`outbox_event` has no foreign keys on purpose: it is written in the same
transaction as the claim change and read only by the relay.

## reporting_db (read model, owned by reporting-service)

```mermaid
erDiagram
    claim_summary {
        uuid claim_id PK
        varchar claim_number
        varchar market_code
        varchar line_of_business
        varchar status
        uuid officer_id "null while unassigned"
        varchar officer_name
        uuid team_id
        numeric current_reserve "0 once settled or rejected"
        varchar currency
        bigint claim_version "ignore events older than this"
        timestamptz submitted_at
        timestamptz decided_at
        timestamptz settled_at
    }
    processed_event {
        uuid event_id PK
        timestamptz processed_at
    }
```

Workload, performance and exposure are SQL aggregates over `claim_summary`.
Exposure = `SUM(current_reserve)` where status is not `SETTLED` or `REJECTED`,
grouped by market and line of business.

## notification_db (owned by notification-service)

```mermaid
erDiagram
    notification {
        uuid id PK
        uuid recipient_id "claimant or officer"
        uuid claim_id
        varchar claim_number
        varchar type "event type that caused it"
        text message
        timestamptz created_at
        timestamptz read_at
    }
    processed_event {
        uuid event_id PK
        timestamptz processed_at
    }
```

## Designed, not built

| Item | What it would add |
|---|---|
| Manager approval | `approval` table and a `PENDING_APPROVAL` state above an officer authority limit |
| Multiple payments per claim | Drop the unique constraint on `payment.claim_id`; exposure becomes reserve minus paid |
| Payment processing | `PROCESSING` and `FAILED` payment states, driven by a payment service over Kafka |
| FX rates | `fx_rate` table for a single-currency consolidated exposure figure |
