package com.clinic.service;

import com.clinic.entity.Clinic;
import com.clinic.entity.ClinicTiming;
import com.clinic.entity.SlotConfig;
import com.clinic.mapper.SlotMapper;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.ClinicTimingRepository;
import com.clinic.repository.SlotConfigRepository;
import com.clinic.repository.SlotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class SlotServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-05-25T04:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 5, 25);

    @Test
    void getSlots_generatesSlotsForRequestedDateBeforeQuerying() {
        TestContext context = createContext();
        ClinicTiming timing = timing(context.clinic, LocalTime.of(10, 0), LocalTime.of(11, 0));

        Mockito.when(context.clinicTimingRepository.findByDayOfWeekAndIsClosedFalse((short) 1))
                .thenReturn(List.of(timing));
        Mockito.when(context.slotConfigRepository.findByClinicId(1L)).thenReturn(Optional.of(slotConfig(context.clinic)));
        Mockito.when(context.slotRepository.findBySlotDate(Mockito.eq(MONDAY), Mockito.any(Pageable.class)))
                .thenReturn(Page.empty());

        context.slotService.getSlots(MONDAY, 0, 20);

        Mockito.verify(context.slotRepository).insertIfAbsent(
                1L, MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 30), 15);
        Mockito.verify(context.slotRepository).insertIfAbsent(
                1L, MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 0), 15);
        Mockito.verify(context.slotRepository).findBySlotDate(Mockito.eq(MONDAY), Mockito.any(Pageable.class));
    }

    @Test
    void getDateAvailability_generatesRequestedRangeBeforeQuerying() {
        TestContext context = createContext();
        ClinicTiming timing = timing(context.clinic, LocalTime.of(10, 0), LocalTime.of(10, 30));
        LocalDate tuesday = MONDAY.plusDays(1);

        Mockito.when(context.clinicTimingRepository.findByDayOfWeekAndIsClosedFalse(Mockito.anyShort()))
                .thenReturn(List.of(timing));
        Mockito.when(context.slotConfigRepository.findByClinicId(1L)).thenReturn(Optional.of(slotConfig(context.clinic)));
        Mockito.when(context.slotRepository.findDateAvailability(
                        Mockito.eq(MONDAY),
                        Mockito.eq(tuesday),
                        Mockito.any(Pageable.class)))
                .thenReturn(Page.empty());

        context.slotService.getDateAvailability(MONDAY, tuesday, 0, 20);

        Mockito.verify(context.slotRepository).insertIfAbsent(
                1L, MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 30), 15);
        Mockito.verify(context.slotRepository).insertIfAbsent(
                1L, tuesday, LocalTime.of(10, 0), LocalTime.of(10, 30), 15);
        Mockito.verify(context.slotRepository).findDateAvailability(
                Mockito.eq(MONDAY),
                Mockito.eq(tuesday),
                Mockito.any(Pageable.class));
    }

    private static TestContext createContext() {
        Clinic clinic = new Clinic();
        clinic.setId(1L);

        ClinicRepository clinicRepository = Mockito.mock(ClinicRepository.class);
        SlotRepository slotRepository = Mockito.mock(SlotRepository.class);
        SlotConfigRepository slotConfigRepository = Mockito.mock(SlotConfigRepository.class);
        ClinicTimingRepository clinicTimingRepository = Mockito.mock(ClinicTimingRepository.class);
        SlotMapper slotMapper = Mockito.mock(SlotMapper.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        Clock clock = Clock.fixed(NOW, ZONE_ID);

        SlotService slotService = new SlotService(
                clinicRepository,
                slotRepository,
                slotConfigRepository,
                clinicTimingRepository,
                slotMapper,
                new PageResponseFactory(),
                new BookingWindowValidator(ZONE_ID.getId(), clock),
                new PaginationValidator(),
                new AppTime(clock, ZONE_ID),
                auditLogService);
        ReflectionTestUtils.setField(slotService, "maxDaysPerRequest", 31L);

        return new TestContext(
                slotService,
                clinic,
                slotRepository,
                slotConfigRepository,
                clinicTimingRepository);
    }

    private static ClinicTiming timing(Clinic clinic, LocalTime startTime, LocalTime endTime) {
        ClinicTiming timing = new ClinicTiming();
        timing.setClinic(clinic);
        timing.setStartTime(startTime);
        timing.setEndTime(endTime);
        timing.setIsClosed(false);
        return timing;
    }

    private static SlotConfig slotConfig(Clinic clinic) {
        SlotConfig slotConfig = new SlotConfig();
        slotConfig.setClinic(clinic);
        slotConfig.setSlotDurationMinutes(30);
        slotConfig.setMaxPatientsPerSlot(15);
        return slotConfig;
    }

    private record TestContext(
            SlotService slotService,
            Clinic clinic,
            SlotRepository slotRepository,
            SlotConfigRepository slotConfigRepository,
            ClinicTimingRepository clinicTimingRepository) {
    }
}
