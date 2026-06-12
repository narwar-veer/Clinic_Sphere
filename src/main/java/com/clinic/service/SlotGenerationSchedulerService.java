package com.clinic.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotGenerationSchedulerService {

    private final SlotService slotService;
    private final AppTime appTime;

    @Value("${app.slot.generation.max-days-per-request:31}")
    private long maxDays;

    @Scheduled(cron = "0 5 0 * * *", zone = "${app.time-zone:Asia/Kolkata}")
    @Transactional
    public void generateUpcomingSlots() {
        LocalDate from = appTime.today();
        LocalDate to = from.plusDays(maxDays - 1);
        int days = slotService.generateSlotsForRange(from, to);
        log.info("Pre-generated slots for {} day(s)", days);
    }
}