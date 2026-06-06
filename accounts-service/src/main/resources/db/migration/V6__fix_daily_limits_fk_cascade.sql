-- Add ON DELETE CASCADE to daily_limits → accounts FK so orphaned rows are
-- automatically removed when an account is hard-deleted.
-- The original FK was created without a name; PostgreSQL generates daily_limits_account_id_fkey.
ALTER TABLE daily_limits
    DROP CONSTRAINT IF EXISTS daily_limits_account_id_fkey;

ALTER TABLE daily_limits
    ADD CONSTRAINT daily_limits_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id)
    ON DELETE CASCADE ON UPDATE CASCADE;
