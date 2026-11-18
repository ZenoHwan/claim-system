CREATE TABLE payment (
    id           UUID PRIMARY KEY,
    -- one payment per claim: the database, not just the code, refuses a second one
    claim_id     UUID           NOT NULL UNIQUE REFERENCES claim (id),
    payee_name   VARCHAR(200)   NOT NULL,
    method       VARCHAR(20) CHECK (method IN ('BANK_TRANSFER', 'CHEQUE')),
    amount       NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    currency     VARCHAR(3)     NOT NULL,
    status       VARCHAR(20)    NOT NULL CHECK (status IN ('REQUESTED', 'PAID')),
    reference    VARCHAR(100),
    requested_at TIMESTAMPTZ    NOT NULL,
    paid_at      TIMESTAMPTZ,
    CHECK ((status = 'PAID') = (paid_at IS NOT NULL AND method IS NOT NULL AND reference IS NOT NULL))
);
