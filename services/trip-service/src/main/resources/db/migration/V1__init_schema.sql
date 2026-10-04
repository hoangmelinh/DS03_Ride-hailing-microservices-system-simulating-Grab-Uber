-- ==============================================================================
-- GoRide :: Trip Service - V1 Schema Migration
-- Database: goride_trip
-- Baseline: database-design.md v1.0
-- ==============================================================================

-- 1. Bảng trips
CREATE TABLE trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id UUID NOT NULL,
    driver_id UUID,
    pickup_latitude NUMERIC(9,6) NOT NULL,
    pickup_longitude NUMERIC(9,6) NOT NULL,
    pickup_address VARCHAR(255),
    destination_latitude NUMERIC(9,6) NOT NULL,
    destination_longitude NUMERIC(9,6) NOT NULL,
    destination_address VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN (
        'REQUESTED',
        'MATCHING',
        'DRIVER_ASSIGNED',
        'ACCEPTED',
        'IN_PROGRESS',
        'COMPLETED',
        'PAYMENT_PENDING',
        'PAID',
        'PAYMENT_FAILED',
        'CANCELLED'
    )),
    estimated_fare NUMERIC(12,2),
    final_fare NUMERIC(12,2),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trips_passenger_time ON trips(passenger_id, requested_at DESC);
CREATE INDEX idx_trips_driver_status ON trips(driver_id, status);
CREATE INDEX idx_trips_status ON trips(status);

-- 2. Bảng trip_status_history
CREATE TABLE trip_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    actor_type VARCHAR(30) NOT NULL CHECK (actor_type IN ('USER', 'DRIVER', 'SYSTEM', 'MATCHING', 'PAYMENT')),
    actor_id UUID,
    correlation_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_history_trip ON trip_status_history(trip_id);

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

CREATE INDEX idx_trip_outbox_pending ON outbox_event(status, next_retry_at);

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

CREATE INDEX idx_trip_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_trip_dlq_queue ON dead_letter_messages(queue_name);
