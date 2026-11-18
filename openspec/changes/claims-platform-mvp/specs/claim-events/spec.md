# Spec Delta

## Purpose

Defines the events the claims service publishes when a claim changes and the delivery guarantees that reporting, notification and any future consumer can rely on.

## ADDED Requirements

### Requirement: An event for every claim state change
The system SHALL publish one event for each of: claim submitted, claim assigned, information requested, information provided, claim assessed, claim approved, claim rejected and claim settled.

#### Scenario: Full lifecycle
- **WHEN** a claim is submitted, picked up, assessed, approved and settled
- **THEN** ClaimSubmitted, ClaimAssigned, ClaimAssessed, ClaimApproved and ClaimSettled are published in that order

### Requirement: Events carry a full claim snapshot
Each event SHALL carry a unique event id, its type, the time it occurred, the claim's version, and a snapshot of the claim: id, number, market, line of business, status, claimant, assigned staff member and team, current reserve, currency and key dates. A consumer MUST NOT need to call the claims service to process an event.

#### Scenario: Consumer builds its view from the event alone
- **WHEN** a consumer receives ClaimAssessed
- **THEN** the event contains the claim's current status, assigned officer and current reserve

### Requirement: No event without the change, no change without the event
An event SHALL be published if and only if the corresponding claim change was committed. A failed or rolled-back change MUST NOT produce an event, and a committed change MUST eventually produce one.

#### Scenario: Change rolled back
- **WHEN** a claim action fails validation or loses a concurrency race
- **THEN** no event is published for it

#### Scenario: Broker unavailable
- **WHEN** a claim change is committed while the message broker is down
- **THEN** the action still succeeds for the user and the event is published once the broker is available again

### Requirement: Ordered per claim, delivered at least once
Events for the same claim SHALL be delivered in the order the changes happened. An event MAY be delivered more than once and consumers MUST produce the same result when it is.

#### Scenario: Duplicate delivery
- **WHEN** the same event is delivered twice to a consumer
- **THEN** the consumer's stored data is the same as if it had been delivered once

#### Scenario: Stale event
- **WHEN** a consumer receives an event whose claim version is older than the one it has already applied
- **THEN** the consumer ignores it

### Requirement: Poison messages do not block the stream
A consumer that cannot process an event after a bounded number of retries SHALL set that event aside on a dead-letter topic and continue with later events.

#### Scenario: Unprocessable event
- **WHEN** an event repeatedly fails to process
- **THEN** it is moved to the dead-letter topic and the next event is processed
