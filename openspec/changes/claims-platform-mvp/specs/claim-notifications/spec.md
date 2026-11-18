# Spec Delta

## Purpose

Tells claimants and staff, inside the application and without them having to refresh, when a claim they are involved in changes.

## ADDED Requirements

### Requirement: Claimant is notified of progress
The system SHALL create a notification for the claimant when their claim is picked up, when information is requested, when it is approved, when it is rejected and when it is settled.

#### Scenario: Information requested
- **WHEN** an officer requests information on a claim
- **THEN** the claimant has a new unread notification naming the claim number and saying more information is needed

#### Scenario: Decision
- **WHEN** a claim is approved or rejected
- **THEN** the claimant has a new unread notification stating the outcome

### Requirement: Assigned staff are notified of responses
The system SHALL create a notification for the assigned staff member when the claimant responds to an information request.

#### Scenario: Claimant responds
- **WHEN** a claimant answers an information request
- **THEN** the assigned officer has a new unread notification naming the claim number

### Requirement: Notification inbox
A signed-in user SHALL be able to list their own notifications newest first, see how many are unread, and mark a notification as read. A user MUST NOT see another user's notifications.

#### Scenario: Unread count
- **WHEN** a user has two unread notifications
- **THEN** the inbox reports an unread count of two

#### Scenario: Mark as read
- **WHEN** the user marks one as read
- **THEN** the unread count is one

#### Scenario: Another user's notification
- **WHEN** a user attempts to mark another user's notification as read
- **THEN** the system responds with 404 Not Found

### Requirement: Live delivery
A signed-in user with the application open SHALL receive new notifications without refreshing the page.

#### Scenario: Notification arrives while the page is open
- **WHEN** a notification is created for a user who has the application open
- **THEN** it appears and the unread count increases without a page reload

#### Scenario: User was offline
- **WHEN** a notification is created while the user is signed out
- **THEN** it is in their inbox the next time they sign in

### Requirement: One notification per event
The system SHALL create at most one notification per recipient for a given event, even if that event is delivered more than once.

#### Scenario: Duplicate event
- **WHEN** the same InformationRequested event is delivered twice
- **THEN** the claimant has exactly one notification for it

### Requirement: Email is stubbed
The system SHALL record that an email would have been sent for each notification without contacting any mail provider.

#### Scenario: Stubbed email
- **WHEN** a notification is created
- **THEN** a log entry records the recipient and subject of the email that would be sent, and no external call is made
