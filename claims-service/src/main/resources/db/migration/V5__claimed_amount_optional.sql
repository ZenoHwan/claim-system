-- At first notification a claimant often has no repair quote yet, so the amount is an optional estimate.
-- The existing CHECK (claimed_amount > 0) still applies whenever a value is given.
ALTER TABLE claim ALTER COLUMN claimed_amount DROP NOT NULL;
