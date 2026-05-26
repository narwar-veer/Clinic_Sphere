package com.clinic.service;

import com.clinic.entity.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentStatusAutomationService {

    private final AppointmentRepository appointmentRepository;

    @Value("${app.appointment.eod-zone:Asia/Kolkata}")
    private String eodZoneId;

    @Scheduled(
            cron = "${app.appointment.eod-cron:0 10 * * * *}",
            zone = "${app.appointment.eod-zone:Asia/Kolkata}"
    )
    @Transactional
    public void markBookedAsNotVisitedAfterEod() {
        LocalDate today = LocalDate.now(ZoneId.of(eodZoneId));
        int updated = appointmentRepository.bulkUpdateStatusBeforeDate(
                AppointmentStatus.BOOKED.name(),
                AppointmentStatus.NOT_VISITED.name(),
                today
        );
        if (updated > 0) {
            log.info("Auto-updated {} appointment(s) from BOOKED to NOT_VISITED before {}", updated, today);
        }
    }
}
