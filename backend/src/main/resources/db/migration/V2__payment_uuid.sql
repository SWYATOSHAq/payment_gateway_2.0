ALTER TABLE payments
    ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT payments_uuid_unique UNIQUE (uuid);
