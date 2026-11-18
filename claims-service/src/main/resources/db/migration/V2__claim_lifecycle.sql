CREATE SEQUENCE claim_number_seq START 1;

CREATE TABLE claim (
    id                   UUID PRIMARY KEY,
    claim_number         VARCHAR(20)    NOT NULL UNIQUE,
    policy_id            UUID           NOT NULL REFERENCES policy (id),
    claimant_id          UUID           NOT NULL REFERENCES app_user (id),
    assigned_officer_id  UUID REFERENCES app_user (id),
    -- copied from the policy at submission so a claim never changes when a policy does
    line_of_business     VARCHAR(20)    NOT NULL,
    market_code          VARCHAR(2)     NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    status               VARCHAR(20)    NOT NULL CHECK (status IN
        ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'APPROVED', 'REJECTED', 'SETTLED')),
    incident_date        DATE           NOT NULL,
    incident_description TEXT           NOT NULL,
    claimed_amount       NUMERIC(15, 2) NOT NULL CHECK (claimed_amount > 0),
    -- latest reserve row, kept here so reads and events do not need a subquery
    current_reserve      NUMERIC(15, 2) NOT NULL CHECK (current_reserve >= 0),
    version              BIGINT         NOT NULL,
    submitted_at         TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    decided_at           TIMESTAMPTZ,
    settled_at           TIMESTAMPTZ
);

CREATE INDEX claim_claimant_idx ON claim (claimant_id);
CREATE INDEX claim_officer_idx ON claim (assigned_officer_id);
CREATE INDEX claim_queue_idx ON claim (market_code, status, submitted_at);

CREATE TABLE motor_claim_detail (
    claim_id             UUID PRIMARY KEY REFERENCES claim (id),
    vehicle_registration VARCHAR(20)  NOT NULL,
    driver_name          VARCHAR(200) NOT NULL,
    accident_location    VARCHAR(300) NOT NULL,
    third_party_involved BOOLEAN      NOT NULL
);

CREATE TABLE property_claim_detail (
    claim_id         UUID PRIMARY KEY REFERENCES claim (id),
    property_address VARCHAR(300) NOT NULL,
    loss_cause       VARCHAR(20)  NOT NULL CHECK (loss_cause IN ('FIRE', 'WATER', 'THEFT', 'STORM', 'OTHER'))
);

CREATE TABLE claim_status_history (
    id          UUID PRIMARY KEY,
    claim_id    UUID        NOT NULL REFERENCES claim (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    actor_id    UUID        NOT NULL REFERENCES app_user (id),
    reason      TEXT,
    changed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX claim_status_history_claim_idx ON claim_status_history (claim_id, changed_at);

CREATE TABLE reserve (
    id         UUID PRIMARY KEY,
    claim_id   UUID           NOT NULL REFERENCES claim (id),
    amount     NUMERIC(15, 2) NOT NULL CHECK (amount >= 0),
    reason     VARCHAR(20)    NOT NULL CHECK (reason IN ('INITIAL', 'ASSESSMENT', 'RELEASED')),
    set_by     UUID REFERENCES app_user (id),
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE INDEX reserve_claim_idx ON reserve (claim_id, created_at);

CREATE TABLE assessment (
    id                 UUID PRIMARY KEY,
    claim_id           UUID           NOT NULL UNIQUE REFERENCES claim (id),
    assessed_by        UUID           NOT NULL REFERENCES app_user (id),
    findings           TEXT           NOT NULL,
    recommended_amount NUMERIC(15, 2) NOT NULL CHECK (recommended_amount >= 0),
    assessed_at        TIMESTAMPTZ    NOT NULL
);

CREATE TABLE decision (
    id              UUID PRIMARY KEY,
    claim_id        UUID        NOT NULL UNIQUE REFERENCES claim (id),
    outcome         VARCHAR(20) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    approved_amount NUMERIC(15, 2),
    reason          TEXT,
    decided_by      UUID        NOT NULL REFERENCES app_user (id),
    decided_at      TIMESTAMPTZ NOT NULL,
    CHECK ((outcome = 'APPROVED') = (approved_amount IS NOT NULL))
);

-- Written in the same transaction as the claim change; read only by the relay.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    event_type   VARCHAR(40) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox_event (created_at) WHERE published_at IS NULL;
