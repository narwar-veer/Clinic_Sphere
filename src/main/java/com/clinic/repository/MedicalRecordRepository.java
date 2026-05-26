package com.clinic.repository;

import com.clinic.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    @EntityGraph(attributePaths = {"doctor", "appointment", "appointment.slot"})
    Page<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    boolean existsByAppointmentId(Long appointmentId);
}
