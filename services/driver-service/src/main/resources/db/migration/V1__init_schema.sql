-- ==============================================================================
-- GoRide :: Driver Service - V1 Schema Migration
-- Database: goride_driver
-- Baseline: database-design.md v1.0
-- ==============================================================================

-- 1. Bảng drivers
CREATE TABLE drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    license_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' CHECK (status IN ('OFFLINE', 'AVAILABLE', 'RESERVED', 'ON_TRIP')),
    current_trip_id UUID,
    reserved_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_drivers_user_id UNIQUE (user_id),
    CONSTRAINT uq_drivers_license_number UNIQUE (license_number)
);

CREATE INDEX idx_drivers_status ON drivers(status);

-- 2. Bảng vehicles
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    plate_number VARCHAR(20) NOT NULL,
    brand VARCHAR(80),
    model VARCHAR(80),
    color VARCHAR(50),
    capacity SMALLINT DEFAULT 4,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vehicles_plate_number UNIQUE (plate_number)
);

CREATE INDEX idx_vehicles_driver_id ON vehicles(driver_id);

-- 3. Bảng driver_locations (vị trí gần nhất trong PostgreSQL)
CREATE TABLE driver_locations (
    driver_id UUID PRIMARY KEY REFERENCES drivers(id) ON DELETE CASCADE,
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Bảng driver_reservations
CREATE TABLE driver_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id),
    trip_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    idempotency_key VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_driver_reservations_idempotency UNIQUE (idempotency_key)
);

-- Invariants bảo vệ Concurrency: Một tài xế chỉ có 1 active reservation, một trip chỉ giữ 1 tài xế
CREATE UNIQUE INDEX uq_driver_active_reservation
ON driver_reservations(driver_id)
WHERE status IN ('RESERVED', 'CONFIRMED');

CREATE UNIQUE INDEX uq_trip_active_driver
ON driver_reservations(trip_id)
WHERE status IN ('RESERVED', 'CONFIRMED');

-- 5. Bảng outbox_event (Technical reliability)
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

CREATE INDEX idx_driver_outbox_pending ON outbox_event(status, next_retry_at);

-- 6. Bảng inbox_event (Idempotent Consumer)
CREATE TABLE inbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    correlation_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Bảng idempotency_record
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

-- 8. Bảng dead_letter_messages (DLQ Error Storage & Re-drive)
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

CREATE INDEX idx_driver_dlq_status ON dead_letter_messages(status);
CREATE INDEX idx_driver_dlq_queue ON dead_letter_messages(queue_name);

