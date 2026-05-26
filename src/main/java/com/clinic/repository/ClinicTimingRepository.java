package com.clinic.repository;

import com.clinic.entity.ClinicTiming;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicTimingRepository extends JpaRepository<ClinicTiming, Long> {

    Optional<ClinicTiming> findByClinicIdAndDayOfWeek(Long clinicId, Short dayOfWeek);

    List<ClinicTiming> findByDayOfWeekAndIsClosedFalse(Short dayOfWeek);
}
