# identity-access Specification

## Purpose

Controls who can sign in to the claims platform, through which door, and what each role is allowed to reach.

## Requirements

### Requirement: Separate sign-in doors for claimants and staff
The system SHALL offer a claimant sign-in and a separate staff sign-in. An account SHALL only be able to sign in through the door that matches its role, and the check MUST be enforced by the server, not only by the screen shown.

#### Scenario: Claimant signs in at the claimant door
- **WHEN** a claimant submits valid credentials at the claimant sign-in
- **THEN** the system issues an access token carrying the user's identity, role and market, and the claimant lands on their claims list

#### Scenario: Claimant tries the staff door
- **WHEN** a claimant submits valid credentials at the staff sign-in
- **THEN** the system refuses the sign-in and issues no token

#### Scenario: Staff member tries the claimant door
- **WHEN** an officer or manager submits valid credentials at the claimant sign-in
- **THEN** the system refuses the sign-in and issues no token

#### Scenario: Wrong password
- **WHEN** any user submits an incorrect password
- **THEN** the system refuses the sign-in without revealing whether the account exists

### Requirement: Every request is authenticated
Every service SHALL reject a request that does not carry a valid, unexpired access token, apart from the sign-in endpoint itself. Each service MUST verify the token itself.

#### Scenario: Missing token
- **WHEN** a request without an access token reaches any protected endpoint of any service
- **THEN** the service responds with 401 Unauthorized

#### Scenario: Tampered token
- **WHEN** a request carries a token whose signature does not verify
- **THEN** the service responds with 401 Unauthorized

### Requirement: Role-based access
The system SHALL support three roles: claimant, officer and manager. A claimant SHALL only reach their own policies, claims, documents and notifications. An officer SHALL reach staff claim handling for their own market. A manager SHALL have every officer permission and additionally reach reporting dashboards.

#### Scenario: Claimant requests another claimant's claim
- **WHEN** a claimant requests a claim they did not submit
- **THEN** the system responds with 404 Not Found

#### Scenario: Claimant calls a staff operation
- **WHEN** a claimant calls an operation reserved for staff
- **THEN** the system responds with 403 Forbidden

#### Scenario: Officer opens the dashboard
- **WHEN** an officer requests manager reporting figures
- **THEN** the system responds with 403 Forbidden

#### Scenario: Manager handles a claim
- **WHEN** a manager picks up, assesses or decides a claim in their market
- **THEN** the system allows it exactly as it would for an officer

### Requirement: Seeded accounts for local use
The system SHALL start with seeded claimant, officer and manager accounts, teams and policies so that it can be exercised without a registration flow. Passwords MUST be stored hashed.

#### Scenario: Fresh start
- **WHEN** the platform is started against empty databases
- **THEN** at least one claimant with a motor and a property policy, one officer and one manager exist and can sign in
