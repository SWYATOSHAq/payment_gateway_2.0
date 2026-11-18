ALTER TABLE payments
    ADD COLUMN checkout_provider VARCHAR(20),
    ADD COLUMN external_payment_id VARCHAR(64) UNIQUE,
    ADD COLUMN confirmation_token TEXT;

UPDATE payments SET checkout_provider = 'CLOUDPAYMENTS' WHERE checkout_created_at IS NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT payments_checkout_provider_check CHECK (
        (checkout_provider IS NULL AND checkout_created_at IS NULL)
        OR (checkout_provider IS NOT NULL AND checkout_created_at IS NOT NULL
            AND checkout_provider IN ('CLOUDPAYMENTS', 'YOOKASSA'))
    );
