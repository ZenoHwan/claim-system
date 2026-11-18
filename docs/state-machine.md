# State Machines

## Claim

Solid states are built. `WITHDRAWN` and `PENDING_APPROVAL` are designed only.

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : claimant submits\n(initial reserve = claimed amount)

    SUBMITTED --> UNDER_REVIEW : officer picks up
    UNDER_REVIEW --> INFO_REQUESTED : officer requests information
    INFO_REQUESTED --> UNDER_REVIEW : claimant responds
    UNDER_REVIEW --> ASSESSED : officer assesses\n(reserve revised)

    ASSESSED --> APPROVED : officer approves\n(payment REQUESTED)
    ASSESSED --> REJECTED : officer rejects\n(reserve released)
    APPROVED --> SETTLED : payment recorded as PAID\n(reserve released)

    SETTLED --> [*]
    REJECTED --> [*]

    SUBMITTED --> WITHDRAWN : future
    UNDER_REVIEW --> WITHDRAWN : future
    INFO_REQUESTED --> WITHDRAWN : future
    ASSESSED --> PENDING_APPROVAL : future, above authority limit
    PENDING_APPROVAL --> APPROVED : future, manager signs off
    PENDING_APPROVAL --> REJECTED : future
    WITHDRAWN --> [*]

    classDef future stroke-dasharray: 5 5
    class WITHDRAWN, PENDING_APPROVAL future
```

### Transition rules

| Transition | Who | Guard | Event published |
|---|---|---|---|
| submit | Claimant | Policy belongs to claimant; incident date inside policy period | `ClaimSubmitted` |
| pick up | Officer | Claim unassigned; officer's market matches; version matches | `ClaimAssigned` |
| request information | Assigned officer | No other request open | `InformationRequested` |
| respond | Claimant who owns the claim | Request is open | `InformationProvided` |
| assess | Assigned officer | Findings and recommended amount present | `ClaimAssessed` |
| approve | Assigned officer | Approved amount greater than zero and not above sum insured | `ClaimApproved` |
| reject | Assigned officer | Reason present | `ClaimRejected` |
| settle | Assigned officer | Payment method and reference recorded | `ClaimSettled` |

"Officer" means any staff member: a manager can do everything an officer can
(pick up, request information, assess, decide, settle) and additionally sees
the dashboard.

Any other transition is refused with `409 Conflict`. So is a stale `version`,
which is how two officers picking up the same claim is resolved: one wins, the
other gets a 409 and a refreshed queue.

Every successful transition writes a `claim_status_history` row and an
`outbox_event` row in the same transaction as the claim update.

### Open versus closed

Open (counted in workload and exposure): `SUBMITTED`, `UNDER_REVIEW`,
`INFO_REQUESTED`, `ASSESSED`, `APPROVED`.
Closed: `SETTLED`, `REJECTED`.

## Payment

```mermaid
stateDiagram-v2
    [*] --> REQUESTED : claim approved
    REQUESTED --> PAID : officer records method + reference
    PAID --> [*]

    REQUESTED --> PROCESSING : future, sent to bank
    PROCESSING --> PAID : future, bank confirms
    PROCESSING --> FAILED : future, bank rejects
    FAILED --> REQUESTED : future, retry

    classDef future stroke-dasharray: 5 5
    class PROCESSING, FAILED future
```

One payment per claim, enforced by a unique constraint on `payment.claim_id`.

## Information request

```mermaid
stateDiagram-v2
    [*] --> OPEN : officer asks
    OPEN --> ANSWERED : claimant replies (text and optional documents)
    ANSWERED --> [*]
```
