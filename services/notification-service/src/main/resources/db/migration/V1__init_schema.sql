-- ==============================================================================
-- GoRide :: Notification Service - V1 Schema Migration
-- Database: goride_notification
-- Baseline: database-design.md v1.0
-- ==============================================================================

-- 1. Bảng notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_event_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    recipient_role VARCHAR(30),
    trip_id UUID,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'SIMULATED',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    payload JSONB NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT uq_notifications_event_recipient_type UNIQUE (source_event_id, recipient_user_id, type)
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_user_id);
CREATE INDEX idx_notifications_status ON notifications(status);

-- 2. Bảng inbox_event (Idempotent Consumer)
CREATE TABLE inbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    correlation_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Bảng dead_letter_messages (DLQ Error Storage & Re-drive)
CREATE TABLE dead_letter_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_name VARCHAR(100) NOT NULL,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id UUID,
    exception_class VARCHAR(255),
    exception_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'DEAD' CHECK (status IN ('DEAD', 'REPROCESSED', 'RESOLVED', 'DISCARDED')),
    failed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reprocessed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_notification_dlq_queue ON dead_letter_messages(queue_name);
