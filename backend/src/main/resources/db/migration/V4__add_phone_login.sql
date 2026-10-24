ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
CREATE UNIQUE INDEX users_phone_number_unique_idx ON users (phone_number) WHERE phone_number IS NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_need_email_or_phone CHECK (email IS NOT NULL OR phone_number IS NOT NULL);
