UPDATE appointments
SET status = 'VISITED'
WHERE status = 'COMPLETED';

UPDATE appointments
SET status = 'NOT_VISITED'
WHERE status = 'NO_SHOW';

UPDATE appointments
SET status = 'BOOKED'
WHERE status NOT IN ('BOOKED', 'VISITED', 'NOT_VISITED', 'CANCELLED');

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS chk_appointments_status_values;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_status_values
        CHECK (status IN ('BOOKED', 'VISITED', 'NOT_VISITED', 'CANCELLED'));

ALTER TABLE medical_records
    ADD COLUMN IF NOT EXISTS referred_by VARCHAR(120);

ALTER TABLE medical_records
    DROP COLUMN IF EXISTS attachment_url;

CREATE TABLE IF NOT EXISTS admin_sessions (
    token_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    doctor_id BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    last_activity_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires_at ON admin_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_doctor_id ON admin_sessions(doctor_id);
