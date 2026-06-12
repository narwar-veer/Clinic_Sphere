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

    @EntityGraph(attributePaths = {"clinic", "clinic.doctor"})
    Page<Slot> findBySlotDate(LocalDate slotDate, Pageable pageable);

    List<Slot> findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(LocalDate fromDate, LocalDate toDate);

    @Query("select distinct s.slotDate from Slot s where s.slotDate between :fromDate and :toDate")
    List<LocalDate> findGeneratedDatesBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

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

    @Query("select count(s) from Slot s where s.clinic.id = :clinicId and s.slotDate = :slotDate and s.bookedCount > 0")
    long countBookedSlotsOnDate(@Param("clinicId") Long clinicId, @Param("slotDate") LocalDate slotDate);

    @Modifying
    @Query(value = """
            insert into slots (clinic_id, slot_date, start_time, end_time, max_patients, booked_count, is_blocked)
            values (:clinicId, :slotDate, :startTime, :endTime, :maxPatients, 0, false)
            on conflict (clinic_id, slot_date, start_time) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("clinicId") Long clinicId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("maxPatients") Integer maxPatients
    );

    @Query(value = """
            select
              s.slot_date as date,
              count(*) as totalSlots,
              sum(greatest(s.max_patients - s.booked_count, 0)) as slotsLeft
            from slots s
            where s.slot_date between :fromDate and :toDate
            group by s.slot_date
            order by s.slot_date asc
            """,
            countQuery = """
            select count(distinct s.slot_date)
            from slots s
            where s.slot_date between :fromDate and :toDate
            """,
            nativeQuery = true)
    Page<DateAvailabilityProjection> findDateAvailability(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
