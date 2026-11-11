CREATE TABLE merchants (
    id BIGINT PRIMARY KEY CHECK (id > 0),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    website VARCHAR(255) NOT NULL CHECK (btrim(website) <> '')
);
CREATE UNIQUE INDEX merchants_website_unique ON merchants(website);
CREATE TABLE payments (
    id BIGINT PRIMARY KEY CHECK (id > 0),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'RUB'),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    merchant_id BIGINT NOT NULL REFERENCES merchants(id) ON DELETE RESTRICT
);
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL
    DEFAULT 'CREATED' CHECK (status IN ('CREATED', 'COMPLETED', 'CANCELLED'));
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
