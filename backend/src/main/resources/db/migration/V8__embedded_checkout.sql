ALTER TABLE payments
    ADD COLUMN embedded_checkout BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN checked_transaction_id BIGINT UNIQUE,
    ADD CONSTRAINT payments_embedded_checkout_check
        CHECK (NOT embedded_checkout OR (checkout_created_at IS NOT NULL AND payment_url IS NULL)),
    ADD CONSTRAINT payments_checked_transaction_check
        CHECK (checked_transaction_id IS NULL OR (embedded_checkout AND status = 'CREATED'));
