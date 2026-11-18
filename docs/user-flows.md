# User Flows and Wireframes

One Angular codebase, two lazy-loaded areas, two login screens. Both logins
call the same endpoint; the server refuses a claimant account at the staff door
and the reverse.

| Area | Login | Routes | Roles |
|---|---|---|---|
| Claimant | `/login` | `/claims/...` | CLAIMANT |
| Staff | `/admin/login` | `/admin/...` | OFFICER, MANAGER |

A manager has every officer capability (queue, pick up, workbench) plus the
dashboard, which officers do not see.

## Claimant flow

```mermaid
flowchart TD
    L["/login"] --> MC["/claims - My claims"]
    MC -->|New claim| S1["Step 1: choose policy"]
    L --> MP["/claims/policies - My policies"]
    MP -->|Open a policy| PD["/claims/policies/:id - Cover and claim history"]
    MP -->|Make a claim| S1
    PD -->|Make a claim, policy preselected| S1
    PD -->|Open a claim| CD
    L --> FU["/claims/follow-ups - Questions waiting for a reply (count on the tab)"]
    FU -->|Reply| IR
    S1 --> S2["Step 2: incident date + description"]
    S2 --> S3["Step 3: motor or property details, address lines and postal code"]
    S3 --> S4["Step 4: attach documents"]
    S4 --> S5["Step 5: review and submit"]
    S5 --> CD["/claims/:id - Claim detail"]
    MC -->|Open a claim| CD
    CD --> TL["Timeline of status changes"]
    CD -->|If information requested| IR["Reply + attach or remove documents"]
    CD --> QA["Questions and replies trail, with each reply's files"]
    IR --> CD
    CD --> DEC["Decision and payment status"]
    BELL(("Notification bell")) -.->|click| CD
    BELL -.->|opening it marks all read| BELL
```

## Staff flow

```mermaid
flowchart TD
    AL["/admin/login"] --> Q["/admin/queue - Unassigned claims"]
    Q -->|Pick up| WB["/admin/claims/:id - Workbench"]
    AL --> MY["/admin/my-claims"]
    MY --> WB
    AL --> FS["/admin/follow-ups - Replies to review (count on the tab) and questions still with the claimant"]
    FS -->|Open claim| WB
    WB -->|Need more| RI["Request information"]
    RI -.->|waits for claimant| WB
    WB --> AS["Assess: findings + recommended amount"]
    AS --> DC{"Decide"}
    DC -->|Approve| PAY["Record payment"]
    DC -->|Reject| CL["Closed: REJECTED"]
    PAY --> ST["Closed: SETTLED"]
    AL -->|Manager only| DB["/admin/dashboard"]
    DB --> W["Workload by officer and status"]
    DB --> P["Performance: decided in 30 days, avg days to decision"]
    DB --> E["Exposure by market and line"]
```

## Wireframes (low fidelity)

### Claimant: My claims (`/claims`)

```
+------------------------------------------------------------------+
| Claims Portal  My claims | My policies | Follow-ups (1) (bell 2) |
+------------------------------------------------------------------+
| My claims                                        [ + New claim ] |
|                                                                  |
| Claim no.        Type      Incident     Status          Updated  |
| ---------------------------------------------------------------- |
| CLM-2026-000123  Motor     12 Nov 2026  INFO REQUESTED  1h ago  >|
| CLM-2026-000098  Property  02 Oct 2026  SETTLED         3w ago  >|
+------------------------------------------------------------------+
```

### Claimant: New claim stepper (`/claims/new`)

```
+------------------------------------------------------------------+
| New claim                                                        |
| (1) Policy -- (2) Incident -- (3) Details -- (4) Docs -- (5) Review
+------------------------------------------------------------------+
| Step 3 of 5: Motor details                                       |
|                                                                  |
| Vehicle registration  [ WXY 1234        ]  e.g. WXY 1234         |
| Driver name           [ Aisha Rahman    ]                        |
| Accident location                                                |
|   Address line 1      [ Jalan Ampang                ]            |
|   Address line 2      [                             ] (optional) |
|   Address line 3      [                             ] (optional) |
|   Postal code         [ 50450 ]  5 digits                        |
| [ ] A third party was involved                                   |
|                                                                  |
| Each field shows its own message, e.g. "Enter the 5-digit postal |
| code"; Next is refused until the step is valid.                  |
|                                                                  |
|                                        [ Back ]   [ Next ]       |
+------------------------------------------------------------------+
```

### Claimant: Claim detail (`/claims/:id`)

```
+------------------------------------------------------------------+
| < My claims     CLM-2026-000123   Motor   [ INFO REQUESTED ]     |
+------------------------------------------------------------------+
| ! More information needed                                        |
|   "Please upload the police report."      - Officer Tan, 1h ago  |
|   [ Your reply...                                  ]             |
|   [ Attach files ]  police-report.pdf (1.2 MB) [Remove] [ Send ] |
+------------------------------------------------------------------+
| Timeline                        | Details                        |
| o 18 Nov 10:02 Info requested   | Policy     MY-MOT-000451       |
| o 18 Nov 09:40 Under review     | Incident   12 Nov 2026         |
| o 18 Nov 09:15 Submitted        | Claimed    MYR 8,500.00        |
|                                 | Documents  photo1.jpg          |
+------------------------------------------------------------------+
```

### Claimant: Policy (`/claims/policies/:id`)

```
+------------------------------------------------------------------+
| < My policies    MY-MOT-000451                   [ Make a claim ]|
+------------------------------------------------------------------+
| Policy details                                                   |
| Type         Motor            Sum insured   MYR 80,000.00        |
| Status       Active           Market        Malaysia             |
| Cover        1 Jan 2026 to 31 Dec 2027      Claims made  2       |
|                                                                  |
| Claims on this policy                                            |
| Claim no.        Incident     Claimed        Status    Updated   |
| CLM-2026-000098  02 Oct 2026  MYR 3,200.00   SETTLED   3w ago    |
+------------------------------------------------------------------+
```

"Make a claim" is offered only when no claim on the policy is in progress.

### Staff: Follow-ups (`/admin/follow-ups`)

```
+------------------------------------------------------------------+
| Follow-ups                                                       |
| Replies to review (1)                                            |
|  CLM-2026-000123  Tan asked 18 Nov, 10:02                        |
|  "Please upload the police report."                              |
|  Replied 18 Nov, 11:15: Report attached.          [ Open claim ] |
|                                                                  |
| Waiting for the claimant (2)                                     |
|  CLM-2026-000131  ...                             [ Open claim ] |
+------------------------------------------------------------------+
```

The tab's count is the replies to review; for a claimant it is the questions waiting for their
reply. Both are worked out from the claim's status and its latest question, so they clear
themselves when the claim moves on.

### Staff: Unassigned queue (`/admin/queue`)

```
+------------------------------------------------------------------+
| Claims Admin  Queue | My claims | Follow-ups (1) | Dashboard (b1)|
+------------------------------------------------------------------+
| Unassigned claims - Malaysia                  Line: [ All    v ] |
|                                                                  |
| Claim no.        Line      Claimed        Age                    |
| ---------------------------------------------------------------- |
| CLM-2026-000131  Property  MYR 42,000.00  3 days    [ Pick up ]  |
| CLM-2026-000129  Motor     MYR  3,200.00  1 day     [ Pick up ]  |
+------------------------------------------------------------------+
```

### Staff: Claim workbench (`/admin/claims/:id`)

```
+------------------------------------------------------------------+
| < My claims   CLM-2026-000123   Motor   MY   [ UNDER REVIEW ]    |
+------------------------------------------------------------------+
| Claim               | Actions                                    |
| Claimant  A. Rahman | [ Request information ]                    |
| Policy    MY-MOT-.. |                                            |
| Sum ins.  MYR 80,000| Assessment                                 |
| Claimed   MYR 8,500 | Findings   [                          ]    |
| Reserve   MYR 8,500 | Recommended amount  [ MYR 7,200.00 ]       |
| Incident  12 Nov    |                         [ Save assessment ]|
|---------------------|--------------------------------------------|
| Documents           | Decision (enabled once assessed)           |
| photo1.jpg    [get] | [ Approve ]  [ Reject ]                    |
| police-rep.pdf[get] | Payment (enabled once approved)            |
|---------------------| Method [ Bank transfer v ] Ref [        ]  |
| Timeline            |                         [ Record payment ] |
| o Info provided ... |                                            |
+------------------------------------------------------------------+
```

### Manager: Dashboard (`/admin/dashboard`)

```
+------------------------------------------------------------------+
| Claims Admin  Queue | My claims | Follow-ups | Dashboard   Lim v |
+------------------------------------------------------------------+
| As of 18 Nov 2026 10:05:31                                       |
|                                                                  |
| Open 47   Unassigned 9   Decided (30d) 112   Avg days to decide 6.4
|                                                                  |
| Workload by officer            | Ageing of open claims           |
| Officer   Open  Info req  Assd | 0-2 days   ########   18        |
| Tan         12      3      2   | 3-7 days   ######     14        |
| Kumar        9      1      4   | 8+ days    #######    15        |
| Wong        17      5      1   |                                 |
|--------------------------------+---------------------------------|
| Outstanding exposure (current reserve on open claims)            |
| Market  Motor            Property          Total                 |
| MY      MYR   412,000    MYR 1,930,000     MYR 2,342,000         |
| SG      SGD   188,500    SGD   640,000     SGD   828,500         |
+------------------------------------------------------------------+
```

The "as of" timestamp is deliberate: the dashboard is built from events and
can trail the claims service by a few seconds.
