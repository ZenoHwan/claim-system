# claims-reporting Specification

## Purpose

Gives staff and managers a consolidated, near-real-time view of claim workload, team performance and outstanding liability exposure, built from claim events.

## Requirements

### Requirement: Workload view
A manager SHALL be able to see, for their market, the number of open claims per staff member broken down by status, the number of unassigned claims, and open claims grouped by age since submission into 0 to 2 days, 3 to 7 days and 8 or more days. Open means not SETTLED and not REJECTED.

#### Scenario: Workload by officer
- **WHEN** a manager requests the workload view
- **THEN** each staff member with open claims is listed with their count per status

#### Scenario: Unassigned count
- **WHEN** three submitted claims in the market have not been picked up
- **THEN** the unassigned count is three

#### Scenario: Closed claims excluded
- **WHEN** a claim is settled or rejected
- **THEN** it no longer counts toward any workload figure

### Requirement: Performance view
A manager SHALL be able to see the number of claims decided in the last 30 days and the average number of days from submission to decision for those claims.

#### Scenario: Average time to decision
- **WHEN** two claims were decided in the last 30 days, 4 and 8 days after submission
- **THEN** the decided count is two and the average is 6 days

#### Scenario: Nothing decided
- **WHEN** no claims were decided in the last 30 days
- **THEN** the decided count is zero and no average is reported

### Requirement: Outstanding exposure
A manager SHALL be able to see outstanding liability exposure as the sum of current reserves on open claims, grouped by market and line of business, in each market's own currency. Amounts in different currencies MUST NOT be added together.

#### Scenario: Unassessed claims count
- **WHEN** a claim for 8,500 MYR has been submitted but not assessed
- **THEN** it contributes 8,500 MYR to motor or property exposure for MY

#### Scenario: Reserve revised
- **WHEN** that claim is assessed at 7,200 MYR
- **THEN** its contribution becomes 7,200 MYR

#### Scenario: Claim closed
- **WHEN** the claim is settled or rejected
- **THEN** its contribution becomes zero

### Requirement: Figures state how current they are
Reporting figures SHALL be derived from claim events and SHALL state the time of the most recent event applied, so that a reader knows how current they are.

#### Scenario: As-of time
- **WHEN** a manager requests any reporting view
- **THEN** the response includes the time of the last event applied

### Requirement: Replays do not distort figures
Reporting figures SHALL be unaffected by an event being delivered more than once or by the event stream being replayed from the start.

#### Scenario: Replayed event
- **WHEN** ClaimAssessed for a claim is delivered a second time
- **THEN** exposure and workload figures are unchanged
