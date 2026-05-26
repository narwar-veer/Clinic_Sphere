package com.clinic.repository;

import com.clinic.entity.Clinic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    List<Clinic> findByDoctorId(Long doctorId);

   Optional<Clinic> findByIdAndDoctorId(Long id, Long doctorId);

    @Override
    @EntityGraph(attributePaths = {"doctor"})
    Page<Clinic> findAll(Pageable pageable);
}
