package com.clinic.repository;

import com.clinic.entity.Patient;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.phone = :phone")
    Optional<Patient> findByPhoneForUpdate(@Param("phone") String phone);
}
