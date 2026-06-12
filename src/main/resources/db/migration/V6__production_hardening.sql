-- Security, reliability, and concurrency hardening

-- Remove one-phone-one-patient uniqueness while keeping phone searchable
ALTER TABLE patients DROP CONSTRAINT IF EXISTS patients_phone_key;
DROP INDEX IF EXISTS idx_patients_phone_lower;
CREATE INDEX IF NOT EXISTS idx_patients_phone ON patients(phone);
CREATE UNIQUE INDEX IF NOT EXISTS uq_patients_identity ON patients(phone, lower(name), age, gender);

-- Appointment idempotency + duplicate booking protection
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS booking_date DATE,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

UPDATE appointments a
SET booking_date = s.slot_date
FROM slots s
WHERE a.slot_id = s.id
  AND a.booking_date IS NULL;

ALTER TABLE appointments
    ALTER COLUMN booking_date SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_idempotency_key
    ON appointments(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_patient_booking_date_active
    ON appointments(patient_id, booking_date)
    WHERE status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_appointments_doctor_booking_date
    ON appointments(booking_date, status);

-- Slot integrity and query performance
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_slots_booked_count_lte_capacity'
    ) THEN
        ALTER TABLE slots
            ADD CONSTRAINT chk_slots_booked_count_lte_capacity
                CHECK (booked_count <= max_patients);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_slots_date_time ON slots(slot_date, start_time);

-- Notification idempotency and auditability
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE notifications
SET idempotency_key = COALESCE(idempotency_key, 'legacy-' || id::text)
WHERE idempotency_key IS NULL;

ALTER TABLE notifications
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_type_idempotency
    ON notifications(type, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_notifications_status_created
    ON notifications(status, created_at DESC);

-- Transactional outbox
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP,
    processed_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','RETRY'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch
    ON outbox_events(status, next_attempt_at, locked_at);

CREATE INDEX IF NOT EXISTS idx_outbox_created
    ON outbox_events(created_at DESC);

-- Audit trail
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    actor VARCHAR(120),
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(120),
    request_id VARCHAR(120),
    details_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_entity_time
    ON audit_logs(entity_type, entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_event_time
    ON audit_logs(event_type, created_at DESC);

-- Session cleanup support
CREATE INDEX IF NOT EXISTS idx_admin_sessions_revoked_at ON admin_sessions(revoked_at);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_last_activity ON admin_sessions(last_activity_at);
