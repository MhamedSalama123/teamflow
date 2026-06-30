ALTER TABLE users
    ADD COLUMN bio          TEXT,
    ADD COLUMN job_title    VARCHAR(150),
    ADD COLUMN location     VARCHAR(150),
    ADD COLUMN phone_number VARCHAR(40),
    ADD COLUMN photo_url    VARCHAR(255),
    ADD COLUMN deleted_at   TIMESTAMP WITH TIME ZONE;
