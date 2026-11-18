# Spec Delta

## Purpose

Defines how a claim is created against a policy and moved by claimants and staff from submission through to a decision, including the permitted transitions, reserve tracking and the audit history.

## ADDED Requirements

### Requirement: Claimant submits a claim against their own policy
A claimant SHALL be able to submit a claim against a policy they hold, giving the incident date, a description, the claimed amount and the details specific to the policy's line of business. The claim SHALL take its line of business, market and currency from the policy and receive a unique claim number.

#### Scenario: Motor claim submitted
- **WHEN** a claimant submits a claim against their motor policy with vehicle registration, driver name, accident location and whether a third party was involved
- **THEN** the claim is created in status SUBMITTED with a claim number, unassigned, and is returned to the claimant

#### Scenario: Property claim submitted
- **WHEN** a claimant submits a claim against their property policy with the property address and cause of loss
- **THEN** the claim is created in status SUBMITTED with a claim number

#### Scenario: Policy belongs to someone else
- **WHEN** a claimant submits a claim against a policy they do not hold
- **THEN** the system rejects the submission and creates no claim

#### Scenario: Incident outside the policy period
- **WHEN** the incident date falls before the policy's start or after its end
- **THEN** the system rejects the submission with a validation error naming the incident date

#### Scenario: Missing or invalid fields
- **WHEN** the claimed amount is zero or negative, the incident date is in the future, or a required field is missing
- **THEN** the system rejects the submission with a validation error listing each invalid field

### Requirement: Initial reserve on submission
The system SHALL record an initial reserve equal to the claimed amount when a claim is submitted, so that every open claim carries a liability figure.

#### Scenario: Reserve created with the claim
- **WHEN** a claim for 8,500 is submitted
- **THEN** the claim's current reserve is 8,500

### Requirement: Claimant tracks their claims
A claimant SHALL be able to list their own claims and open any one of them to see its details, current status, and the dated history of status changes.

#### Scenario: Claims list
- **WHEN** a claimant requests their claims
- **THEN** only claims they submitted are returned, most recently updated first

#### Scenario: Timeline
- **WHEN** a claimant opens one of their claims
- **THEN** the response includes every status change in order with the time it happened

### Requirement: Staff pick up unassigned claims
Staff SHALL be able to list unassigned claims in their own market and pick one up. Picking up SHALL assign the claim to that staff member and move it to UNDER_REVIEW. A claim MUST never be assigned to two people.

#### Scenario: Unassigned queue
- **WHEN** an officer in market MY requests the unassigned queue
- **THEN** only SUBMITTED, unassigned claims from market MY are returned, oldest first

#### Scenario: Successful pick-up
- **WHEN** an officer picks up an unassigned claim
- **THEN** the claim is assigned to that officer and its status becomes UNDER_REVIEW

#### Scenario: Two officers pick up the same claim
- **WHEN** two officers attempt to pick up the same claim at the same time
- **THEN** exactly one succeeds and the other receives 409 Conflict

#### Scenario: Claim from another market
- **WHEN** an officer attempts to pick up a claim from a market other than their own
- **THEN** the system refuses the action

### Requirement: Staff see their own assigned claims
Staff SHALL be able to list the claims assigned to them and open any claim in their market with its full details, documents, information requests, reserve and history.

#### Scenario: My claims
- **WHEN** an officer requests their assigned claims
- **THEN** only claims assigned to that officer are returned

### Requirement: Assigned staff assess a claim
The staff member assigned to a claim SHALL be able to record an assessment with findings and a recommended amount while the claim is UNDER_REVIEW. Assessing SHALL move the claim to ASSESSED and set the current reserve to the recommended amount, keeping the previous reserve as history. A claim SHALL have at most one assessment.

#### Scenario: Assessment recorded
- **WHEN** the assigned officer records findings and a recommended amount of 7,200 on a claim under review
- **THEN** the claim becomes ASSESSED and its current reserve is 7,200

#### Scenario: Not the assigned officer
- **WHEN** an officer who is not assigned to the claim attempts to assess it
- **THEN** the system refuses the action

#### Scenario: Information still outstanding
- **WHEN** an assessment is attempted while the claim is INFO_REQUESTED
- **THEN** the system responds with 409 Conflict

### Requirement: Assigned staff decide a claim
The staff member assigned to an ASSESSED claim SHALL be able to approve it with an approved amount or reject it with a reason. Approval SHALL move the claim to APPROVED. Rejection SHALL move the claim to REJECTED and release its reserve to zero.

#### Scenario: Approval
- **WHEN** the assigned officer approves an assessed claim for 7,200
- **THEN** the claim becomes APPROVED and the decision records the amount, the officer and the time

#### Scenario: Approved amount above the sum insured
- **WHEN** the approved amount exceeds the policy's sum insured
- **THEN** the system rejects the decision with a validation error

#### Scenario: Rejection
- **WHEN** the assigned officer rejects an assessed claim with a reason
- **THEN** the claim becomes REJECTED, its current reserve is zero, and the claimant can see the reason

#### Scenario: Rejection without a reason
- **WHEN** a rejection is submitted with no reason
- **THEN** the system rejects the decision with a validation error

### Requirement: Only permitted transitions are accepted
The system SHALL accept only these status transitions: SUBMITTED to UNDER_REVIEW, UNDER_REVIEW to INFO_REQUESTED, INFO_REQUESTED to UNDER_REVIEW, UNDER_REVIEW to ASSESSED, ASSESSED to APPROVED, ASSESSED to REJECTED, and APPROVED to SETTLED. Any other attempt MUST be refused and leave the claim unchanged.

#### Scenario: Deciding before assessing
- **WHEN** a decision is attempted on a claim that is UNDER_REVIEW
- **THEN** the system responds with 409 Conflict and the claim stays UNDER_REVIEW

#### Scenario: Acting on a closed claim
- **WHEN** any state-changing action is attempted on a SETTLED or REJECTED claim
- **THEN** the system responds with 409 Conflict

### Requirement: Every status change is recorded
The system SHALL record each status change with the previous status, the new status, who made it and when. The history MUST be written together with the change so that neither exists without the other.

#### Scenario: History after pick-up
- **WHEN** an officer picks up a claim
- **THEN** the claim history contains an entry from SUBMITTED to UNDER_REVIEW naming that officer

### Requirement: Consistent error responses
The system SHALL report failures in a single structured error format carrying a status code, a short title and a human-readable detail, with per-field messages for validation failures.

#### Scenario: Validation failure
- **WHEN** a request fails validation
- **THEN** the response is 400 Bad Request and lists each invalid field with its message
