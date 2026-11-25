# Spec Delta

## Purpose

Lets staff pause a claim to ask the claimant for more information, and lets the claimant answer so that the claim returns to review.

## ADDED Requirements

### Requirement: Assigned staff request information
The staff member assigned to a claim that is UNDER_REVIEW SHALL be able to ask the claimant a question. Doing so SHALL create an open information request and move the claim to INFO_REQUESTED.

#### Scenario: Request created
- **WHEN** the assigned officer asks "Please upload the police report" on a claim under review
- **THEN** an open information request with that question exists and the claim is INFO_REQUESTED

#### Scenario: Empty question
- **WHEN** an information request is submitted with no question text
- **THEN** the system rejects it with a validation error

#### Scenario: Claim not under review
- **WHEN** an information request is attempted on a claim that is not UNDER_REVIEW
- **THEN** the system responds with 409 Conflict

### Requirement: Claimant sees open requests
A claimant SHALL see, on their claim, each information request with its question, who asked, when, and whether it has been answered.

#### Scenario: Open request shown
- **WHEN** a claimant opens a claim that is INFO_REQUESTED
- **THEN** the open request and its question are included in the response

### Requirement: Claimant responds to a request
The claimant who owns the claim SHALL be able to answer an open information request with text, optionally attaching documents. Answering SHALL mark the request answered and return the claim to UNDER_REVIEW with the same assigned staff member.

#### Scenario: Response submitted
- **WHEN** the claimant answers the open request with text
- **THEN** the request is ANSWERED with the response and time recorded, and the claim is UNDER_REVIEW

#### Scenario: Responding twice
- **WHEN** a response is submitted to a request that is already answered
- **THEN** the system responds with 409 Conflict

#### Scenario: Someone else's request
- **WHEN** a claimant attempts to answer a request on a claim they do not own
- **THEN** the system responds with 404 Not Found

### Requirement: Repeated requests
Staff SHALL be able to request information again after a response has been received, as many times as needed.

#### Scenario: Second request after a response
- **WHEN** the assigned officer raises a new request on a claim that returned to UNDER_REVIEW
- **THEN** a second open request exists and the earlier answered request remains visible
