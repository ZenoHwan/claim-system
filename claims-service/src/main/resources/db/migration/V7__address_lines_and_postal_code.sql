-- Accident location and property address become three lines plus a postal code, same columns on both tables.
-- Existing claims keep their single-line text as line 1 and have no postal code.
ALTER TABLE motor_claim_detail RENAME COLUMN accident_location TO address_line1;
ALTER TABLE motor_claim_detail
    ADD COLUMN address_line2 VARCHAR(300),
    ADD COLUMN address_line3 VARCHAR(300),
    ADD COLUMN postal_code   VARCHAR(6);

ALTER TABLE property_claim_detail RENAME COLUMN property_address TO address_line1;
ALTER TABLE property_claim_detail
    ADD COLUMN address_line2 VARCHAR(300),
    ADD COLUMN address_line3 VARCHAR(300),
    ADD COLUMN postal_code   VARCHAR(6);
