CREATE TABLE doctors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    photo TEXT,
    degrees VARCHAR(255),
    specialization VARCHAR(120) NOT NULL,
    experience_years INTEGER NOT NULL DEFAULT 0,
    registration_number VARCHAR(80) NOT NULL UNIQUE,
    bio TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE clinics (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_name VARCHAR(150) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(20),
    whatsapp VARCHAR(20),
    email VARCHAR(150),
    map_location TEXT
);

CREATE TABLE clinic_timings (
    id BIGSERIAL PRIMARY KEY,
    clinic_id BIGINT NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME,
    end_time TIME,
    break_start_time TIME,
    break_end_time TIME,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (clinic_id, day_of_week)
);

CREATE TABLE slot_configs (
    id BIGSERIAL PRIMARY KEY,
    clinic_id BIGINT NOT NULL UNIQUE REFERENCES clinics(id) ON DELETE CASCADE,
    slot_duration_minutes INTEGER NOT NULL CHECK (slot_duration_minutes > 0),
    max_patients_per_slot INTEGER NOT NULL CHECK (max_patients_per_slot > 0)
);

CREATE TABLE slots (
    id BIGSERIAL PRIMARY KEY,
    clinic_id BIGINT NOT NULL REFERENCES clinics(id) ON DELETE CASCADE,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    max_patients INTEGER NOT NULL CHECK (max_patients > 0),
    booked_count INTEGER NOT NULL DEFAULT 0 CHECK (booked_count >= 0),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (clinic_id, slot_date, start_time)
);

CREATE TABLE patients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    age INTEGER NOT NULL CHECK (age > 0),
    gender VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    slot_id BIGINT NOT NULL REFERENCES slots(id) ON DELETE RESTRICT,
    symptoms TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medical_records (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,
    diagnosis TEXT NOT NULL,
    prescription_notes TEXT,
    attachment_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP,
    provider_message_id VARCHAR(120),
    error_message TEXT
);

CREATE TABLE admins (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    doctor_id BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE
);

CREATE INDEX idx_slots_date_clinic ON slots(slot_date, clinic_id);
CREATE INDEX idx_appointments_status ON appointments(status);
CREATE INDEX idx_appointments_created ON appointments(created_at DESC);
CREATE INDEX idx_medical_records_patient ON medical_records(patient_id, created_at DESC);

INSERT INTO doctors (name, photo, degrees, specialization, experience_years, registration_number, bio)
VALUES ('Dr Aditi Sharma', NULL, 'MBBS, MD (Internal Medicine)', 'General Physician', 12, 'REG-DEL-112233',
        'Experienced physician focused on preventive care and chronic disease management.');

INSERT INTO clinics (doctor_id, clinic_name, address, phone, whatsapp, email, map_location)
VALUES (1, 'Sharma Clinic', '12 Green Park, New Delhi', '+919999999999', '+919999999999', 'clinic@example.com',
        'https://maps.google.com/?q=Sharma+Clinic');

INSERT INTO clinic_timings (clinic_id, day_of_week, start_time, end_time, break_start_time, break_end_time, is_closed)
VALUES
    (1, 1, '10:00', '14:00', '12:00', '13:00', FALSE),
    (1, 2, '10:00', '14:00', '12:00', '13:00', FALSE),
    (1, 3, '10:00', '14:00', '12:00', '13:00', FALSE),
    (1, 4, '10:00', '14:00', '12:00', '13:00', FALSE),
    (1, 5, '10:00', '14:00', '12:00', '13:00', FALSE),
    (1, 6, '10:00', '13:00', NULL, NULL, FALSE),
    (1, 7, NULL, NULL, NULL, NULL, TRUE);

INSERT INTO slot_configs (clinic_id, slot_duration_minutes, max_patients_per_slot)
VALUES (1, 30, 2);
