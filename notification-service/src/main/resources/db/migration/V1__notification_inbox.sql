CREATE TABLE notification (
    id            uuid PRIMARY KEY,
    recipient_id  uuid         NOT NULL,
    claim_id      uuid         NOT NULL,
    claim_number  varchar(32)  NOT NULL,
    type          varchar(40)  NOT NULL,
    message       text         NOT NULL,
    created_at    timestamptz  NOT NULL,
    read_at       timestamptz
);

CREATE INDEX notification_inbox_idx ON notification (recipient_id, created_at DESC);

-- Kafka delivers at least once; an event id in here has already been handled.
CREATE TABLE processed_event (
    event_id      uuid PRIMARY KEY,
    processed_at  timestamptz NOT NULL
);
