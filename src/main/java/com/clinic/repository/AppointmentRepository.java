package com.clinic.repository;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"patient", "slot", "slot.clinic", "slot.clinic.doctor"})
    Optional<Appointment> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "slot", "slot.clinic", "slot.clinic.doctor"})
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"patient", "slot", "slot.clinic", "slot.clinic.doctor"})
    List<Appointment> findAll(Specification<Appointment> specification, Sort sort);

    @EntityGraph(attributePaths = {"slot"})
    Optional<Appointment> findFirstByPatientIdAndSlotSlotDateAndStatusNotOrderBySlotStartTimeAsc(
            Long patientId,
            LocalDate slotDate,
            AppointmentStatus status
    );

    Optional<Appointment> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select (count(a) > 0)
            from Appointment a
            where a.patient.id = :patientId
              and a.slot.clinic.doctor.id = :doctorId
            """)
    boolean existsByPatientIdAndDoctorId(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE appointments a
            SET status = :targetStatus,
                visited_at = NULL
            FROM slots s
            WHERE a.slot_id = s.id
              AND a.status = :sourceStatus
              AND s.slot_date < :beforeDate
            """, nativeQuery = true)
    int bulkUpdateStatusBeforeDate(
            @Param("sourceStatus") String sourceStatus,
            @Param("targetStatus") String targetStatus,
            @Param("beforeDate") LocalDate beforeDate
    );
}
