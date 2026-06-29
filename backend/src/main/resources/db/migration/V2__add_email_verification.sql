ALTER TABLE users
    ADD COLUMN email_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verification_code           VARCHAR(6),
    ADD COLUMN verification_code_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN verification_attempts       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN verification_locked_until   TIMESTAMP WITH TIME ZONE;
