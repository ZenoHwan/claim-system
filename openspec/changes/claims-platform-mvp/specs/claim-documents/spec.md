# Spec Delta

## Purpose

Lets claimants attach evidence files to a claim and lets the claimant and staff retrieve them, with limits on what may be uploaded and who may download.

## ADDED Requirements

### Requirement: Claimant uploads documents to their claim
A claimant SHALL be able to upload documents to a claim they own while the claim is open. A document uploaded as part of answering an information request SHALL be linked to that request.

#### Scenario: Upload at submission
- **WHEN** a claimant uploads a photo to a claim they have just submitted
- **THEN** the document is stored and appears in the claim's document list with its name, type, size and upload time

#### Scenario: Upload with a response
- **WHEN** a claimant uploads a file while answering an information request
- **THEN** the document is linked to that information request

#### Scenario: Upload to a closed claim
- **WHEN** a claimant uploads a document to a SETTLED or REJECTED claim
- **THEN** the system responds with 409 Conflict

#### Scenario: Staff attempt to upload
- **WHEN** an officer or manager attempts to upload a document
- **THEN** the system responds with 403 Forbidden

### Requirement: Upload limits enforced by the server
The system SHALL accept only PDF, JPEG and PNG files of at most 10 MB each. The checks MUST be made by the server on the received content, regardless of what the client declares.

#### Scenario: Unsupported type
- **WHEN** a file of any other type is uploaded
- **THEN** the system rejects it with a validation error and stores nothing

#### Scenario: File too large
- **WHEN** a file larger than 10 MB is uploaded
- **THEN** the system rejects it with 413 Payload Too Large and stores nothing

#### Scenario: Disguised file
- **WHEN** a file is declared as PDF but its content is not a PDF
- **THEN** the system rejects it with a validation error

### Requirement: Controlled download
A document SHALL be downloadable only by the claimant who owns its claim and by staff in the claim's market. The stored location of a file MUST NOT be derivable from user input.

#### Scenario: Owner downloads
- **WHEN** the owning claimant requests one of their claim's documents
- **THEN** the file is returned with its original name and content type

#### Scenario: Staff download
- **WHEN** an officer in the claim's market requests the document
- **THEN** the file is returned

#### Scenario: Another claimant requests the file
- **WHEN** a different claimant requests the document
- **THEN** the system responds with 404 Not Found
