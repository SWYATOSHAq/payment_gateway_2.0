CREATE TABLE payment_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id BIGINT NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES user_accounts (id),
    body VARCHAR(2000) NOT NULL CHECK (btrim(body) <> ''),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payment_notes_history_idx ON payment_notes (payment_id, pinned DESC, created_at DESC, id DESC);
