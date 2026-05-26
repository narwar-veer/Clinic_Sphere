ALTER TABLE doctors
    ADD COLUMN IF NOT EXISTS awards TEXT;

UPDATE appointments
SET status = 'BOOKED'
WHERE status IN ('PENDING', 'CONFIRMED');

UPDATE appointments
SET status = 'COMPLETED'
WHERE status = 'VISITED';

UPDATE appointments
SET status = 'BOOKED'
WHERE status NOT IN ('BOOKED', 'CANCELLED', 'COMPLETED', 'NO_SHOW');

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS chk_appointments_status_values;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_status_values
        CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
