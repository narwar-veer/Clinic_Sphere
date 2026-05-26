package com.clinic.repository;

import com.clinic.entity.SlotConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotConfigRepository extends JpaRepository<SlotConfig, Long> {

    Optional<SlotConfig> findByClinicId(Long clinicId);
}
