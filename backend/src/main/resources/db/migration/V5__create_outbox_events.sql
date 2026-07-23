CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version SMALLINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner UUID,
    lease_expires_at TIMESTAMPTZ,
    CONSTRAINT ck_outbox_event_version_positive CHECK (event_version > 0),
    CONSTRAINT ck_outbox_publish_attempts_non_negative CHECK (publish_attempts >= 0)
);

CREATE INDEX ix_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
