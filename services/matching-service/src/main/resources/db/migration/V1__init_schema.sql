-- ==============================================================================
-- GoRide :: Matching Service - V1 Schema Migration
-- Database: goride_matching
-- Baseline: database-design.md v1.0
-- ==============================================================================

-- 1. Bảng matching_sagas (Saga Orchestration State)
CREATE TABLE matching_sagas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    selected_driver_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'STARTED' CHECK (status IN (
        'STARTED',
        'CANDIDATE_SELECTED',
        'DRIVER_RESERVED',
        'TRIP_ASSIGNED',
        'COMPLETED',
        'COMPENSATING',
        'COMPENSATED',
        'FAILED'
    )),
    current_step VARCHAR(50),
    correlation_id UUID NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_matching_sagas_trip_id UNIQUE (trip_id),
    CONSTRAINT uq_matching_sagas_correlation_id UNIQUE (correlation_id)
);

CREATE INDEX idx_matching_sagas_status ON matching_sagas(status);

-- 2. Bảng matching_attempts (Lịch sử thử chọn từng candidate driver)
CREATE TABLE matching_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id UUID NOT NULL REFERENCES matching_sagas(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL,
    candidate_rank INTEGER NOT NULL,
    distance_meters INTEGER,
    attempt_no INTEGER NOT NULL,
    reserve_result VARCHAR(30) CHECK (reserve_result IN ('SUCCESS', 'DRIVER_BUSY', 'TIMEOUT', 'ERROR')),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_matching_attempts_saga ON matching_attempts(saga_id);

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

CREATE INDEX idx_matching_outbox_pending ON outbox_event(status, next_retry_at);

-- 4. Bảng inbox_event (Idempotent Consumer)
CREATE TABLE inbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    correlation_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Bảng dead_letter_messages (DLQ Error Storage & Re-drive)
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

CREATE INDEX idx_matching_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_matching_dlq_queue ON dead_letter_messages(queue_name);
