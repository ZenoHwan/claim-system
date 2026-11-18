CREATE TABLE team (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    market_code VARCHAR(2)   NOT NULL
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'MANAGER')),
    market_code   VARCHAR(2)   NOT NULL,
    team_id       UUID REFERENCES team (id),
    -- staff belong to a team, claimants do not
    CONSTRAINT staff_have_a_team CHECK ((role = 'CLAIMANT') = (team_id IS NULL))
);

CREATE TABLE policy (
    id               UUID PRIMARY KEY,
    policy_number    VARCHAR(30)    NOT NULL UNIQUE,
    holder_id        UUID           NOT NULL REFERENCES app_user (id),
    line_of_business VARCHAR(20)    NOT NULL CHECK (line_of_business IN ('MOTOR', 'PROPERTY')),
    market_code      VARCHAR(2)     NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    sum_insured      NUMERIC(15, 2) NOT NULL CHECK (sum_insured > 0),
    effective_from   DATE           NOT NULL,
    effective_to     DATE           NOT NULL,
    CHECK (effective_to >= effective_from)
);

CREATE INDEX policy_holder_idx ON policy (holder_id);
