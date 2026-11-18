-- One row per claim, overwritten by each newer event. Reports are aggregates over this table.
CREATE TABLE claim_summary (
    claim_id         uuid PRIMARY KEY,
    claim_number     varchar(30)    NOT NULL,
    market_code      varchar(2)     NOT NULL,
    line_of_business varchar(20)    NOT NULL,
    status           varchar(30)    NOT NULL,
    officer_id       uuid,
    officer_name     varchar(200),
    team_id          uuid,
    current_reserve  numeric(15, 2) NOT NULL,
    currency         varchar(3)     NOT NULL,
    claim_version    bigint         NOT NULL,
    submitted_at     timestamptz    NOT NULL,
    decided_at       timestamptz,
    settled_at       timestamptz,
    last_event_at    timestamptz    NOT NULL
);

CREATE INDEX claim_summary_market_status ON claim_summary (market_code, status);

-- Event ids already applied, so a redelivered event is dropped.
CREATE TABLE processed_event (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
