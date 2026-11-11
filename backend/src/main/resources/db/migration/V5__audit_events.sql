CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES user_accounts (id) ON DELETE SET NULL,
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    description VARCHAR(250) NOT NULL,
    correlation_id UUID NOT NULL
);

CREATE INDEX audit_events_order_idx ON audit_events (occurred_at, id);
