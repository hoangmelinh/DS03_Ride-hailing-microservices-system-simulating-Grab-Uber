-- ==============================================================================
-- GoRide :: Payment Service - V1 Schema Migration
-- Database: goride_payment
-- Baseline: database-design.md v1.0
-- ==============================================================================

-- 1. Bảng payments (Source of truth cho giao dịch thanh toán)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    provider VARCHAR(30) NOT NULL DEFAULT 'SIMULATED',
    provider_reference VARCHAR(100),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_payments_trip_id UNIQUE (trip_id)
);

CREATE INDEX idx_payments_passenger ON payments(passenger_id);
CREATE INDEX idx_payments_status ON payments(status);

-- 2. Bảng payment_attempts (Lịch sử các lần thử thanh toán / retry)
CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED')),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_payment_attempts_payment ON payment_attempts(payment_id);

-- 3. Bảng outbox_event (Transactional Outbox)
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_outbox_pending ON outbox_event(status, next_retry_at);

-- 4. Bảng inbox_event (Idempotent Consumer)
CREATE TABLE inbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    correlation_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Bảng idempotency_record
CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    operation VARCHAR(100) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- 6. Bảng dead_letter_messages (DLQ Error Storage & Re-drive)
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

CREATE INDEX idx_payment_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_payment_dlq_queue ON dead_letter_messages(queue_name);
