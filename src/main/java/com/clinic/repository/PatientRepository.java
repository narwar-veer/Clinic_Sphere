package com.clinic.repository;

import com.clinic.entity.Gender;
import com.clinic.entity.Patient;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findFirstByPhoneAndNameIgnoreCaseAndAgeAndGenderOrderByCreatedAtDesc(
            String phone,
            String name,
            Integer age,
            Gender gender
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.phone = :phone and lower(p.name) = lower(:name) and p.age = :age and p.gender = :gender")
    Optional<Patient> findByIdentityForUpdate(
            @Param("phone") String phone,
            @Param("name") String name,
            @Param("age") Integer age,
            @Param("gender") Gender gender
    );
}