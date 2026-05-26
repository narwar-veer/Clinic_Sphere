ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS visited_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS testimonial TEXT,
    ADD COLUMN IF NOT EXISTS rating SMALLINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_appointments_rating_range'
    ) THEN
        ALTER TABLE appointments
            ADD CONSTRAINT chk_appointments_rating_range
            CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5));
    END IF;
END $$;
