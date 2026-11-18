-- A policy can carry only one claim at a time; another may be raised once it is settled or rejected.
-- Enforced here as well as in code so that two simultaneous submissions cannot both get through.
CREATE UNIQUE INDEX claim_one_open_per_policy
    ON claim (policy_id)
    WHERE status NOT IN ('SETTLED', 'REJECTED');
