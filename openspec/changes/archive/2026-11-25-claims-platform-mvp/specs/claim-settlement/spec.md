# Spec Delta

## Purpose

Tracks the payment owed on an approved claim and closes the claim as settled once that payment is recorded as made.

## ADDED Requirements

### Requirement: Payment raised on approval
When a claim is approved, the system SHALL create one payment for the approved amount in the claim's currency, in status REQUESTED, with the payee defaulting to the claimant's name.

#### Scenario: Approval creates a payment
- **WHEN** a claim is approved for 7,200 MYR
- **THEN** a REQUESTED payment of 7,200 MYR exists for that claim

#### Scenario: Rejection creates no payment
- **WHEN** a claim is rejected
- **THEN** no payment exists for that claim

### Requirement: One payment per claim
The system SHALL allow at most one payment per claim. The limit MUST hold even under concurrent requests.

#### Scenario: Second payment attempted
- **WHEN** a second payment is attempted for a claim that already has one
- **THEN** it is refused and the existing payment is unchanged

### Requirement: Recording the payment settles the claim
The staff member assigned to an APPROVED claim SHALL be able to record the payment method and reference. Doing so SHALL mark the payment PAID with the time, move the claim to SETTLED and release its reserve to zero.

#### Scenario: Payment recorded
- **WHEN** the assigned officer records a bank transfer with a reference on an approved claim
- **THEN** the payment is PAID, the claim is SETTLED and its current reserve is zero

#### Scenario: Missing reference
- **WHEN** the payment is recorded without a reference
- **THEN** the system rejects it with a validation error and the claim stays APPROVED

#### Scenario: Recording twice
- **WHEN** the payment is recorded a second time
- **THEN** the system responds with 409 Conflict

#### Scenario: Claim not approved
- **WHEN** a payment is recorded for a claim that is not APPROVED
- **THEN** the system responds with 409 Conflict

### Requirement: Claimant sees payment status
A claimant SHALL see, on an approved or settled claim, the approved amount and whether payment is requested or paid, with the paid date once paid. Bank account details MUST NOT be stored or shown.

#### Scenario: Settled claim
- **WHEN** a claimant opens their settled claim
- **THEN** the approved amount, payment status PAID and the paid date are shown
