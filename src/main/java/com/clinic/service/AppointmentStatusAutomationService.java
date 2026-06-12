package com.clinic.service;

import com.clinic.entity.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentStatusAutomationService {

    private final AppointmentRepository appointmentRepository;
    private final AppTime appTime;

    @Scheduled(
            cron = "${app.appointment.eod-cron:0 55 23 * * *}",
            zone = "${app.appointment.eod-zone:Asia/Kolkata}"
    )
    @Transactional
    public void markBookedAsNotVisitedAfterEod() {
        int updated = appointmentRepository.bulkUpdateStatusBeforeDate(
                AppointmentStatus.BOOKED.name(),
                AppointmentStatus.NOT_VISITED.name(),
                appTime.today()
        );
        if (updated > 0) {
            log.info("Auto-updated {} appointment(s) from BOOKED to NOT_VISITED", updated);
        }
    }
}