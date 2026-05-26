package com.clinic.repository;

import com.clinic.entity.Slot;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);

    boolean existsByClinicIdAndSlotDateAndStartTime(Long clinicId, LocalDate slotDate, LocalTime startTime);

    @EntityGraph(attributePaths = {"clinic", "clinic.doctor"})
    Page<Slot> findBySlotDate(LocalDate slotDate, Pageable pageable);

    List<Slot> findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query("update Slot s set s.isBlocked = :blocked where s.clinic.id = :clinicId and s.slotDate = :slotDate")
    int updateBlockedByClinicIdAndSlotDate(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("blocked") Boolean blocked
    );

    @Modifying
    @Query("""
            update Slot s
            set s.maxPatients = :capacity
            where s.clinic.id = :clinicId
              and s.slotDate = :slotDate
              and s.startTime >= :startTime
              and s.bookedCount <= :capacity
            """)
    int updateCapacityForDateFromTime(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("capacity") Integer capacity
    );

    @Modifying
    @Query("""
            update Slot s
            set s.maxPatients = :capacity
            where s.clinic.id = :clinicId
              and s.slotDate > :slotDate
              and s.bookedCount <= :capacity
            """)
    int updateCapacityAfterDate(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("capacity") Integer capacity
    );

    @Query("""
            select count(s)
            from Slot s
            where s.clinic.id = :clinicId
              and s.slotDate = :slotDate
              and s.startTime >= :startTime
              and s.bookedCount > :capacity
            """)
    long countConflictsForDateFromTime(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("capacity") Integer capacity
    );

    @Query("""
            select count(s)
            from Slot s
            where s.clinic.id = :clinicId
              and s.slotDate > :slotDate
              and s.bookedCount > :capacity
            """)
    long countConflictsAfterDate(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("capacity") Integer capacity
    );

    @EntityGraph(attributePaths = {"clinic", "clinic.doctor"})
    Optional<Slot> findById(Long id);
}
