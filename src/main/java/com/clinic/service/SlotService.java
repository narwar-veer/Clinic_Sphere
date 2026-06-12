package com.clinic.service;

import com.clinic.dto.request.SlotConfigUpdateRequest;
import com.clinic.dto.response.MessageResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.dto.response.SlotConfigResponse;
import com.clinic.dto.response.SlotDateAvailabilityResponse;
import com.clinic.dto.response.SlotResponse;
import com.clinic.entity.Clinic;
import com.clinic.entity.ClinicTiming;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotConfig;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.UnauthorizedException;
import com.clinic.mapper.SlotMapper;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.ClinicTimingRepository;
import com.clinic.repository.DateAvailabilityProjection;
import com.clinic.repository.SlotConfigRepository;
import com.clinic.repository.SlotRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private static final int DEFAULT_SLOT_DURATION_MINUTES = 30;

    private final ClinicRepository clinicRepository;
    private final SlotRepository slotRepository;
    private final SlotConfigRepository slotConfigRepository;
    private final ClinicTimingRepository clinicTimingRepository;
    private final SlotMapper slotMapper;
    private final PageResponseFactory pageResponseFactory;
    private final BookingWindowValidator bookingWindowValidator;
    private final PaginationValidator paginationValidator;
    private final AppTime appTime;
    private final AuditLogService auditLogService;

    @Value("${app.slot.generation.max-days-per-request:31}")
    private long maxDaysPerRequest;

    @Transactional
    public void generateSlotsForDate(LocalDate date) {
        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        var timings = clinicTimingRepository.findByDayOfWeekAndIsClosedFalse(dayOfWeek);

        for (ClinicTiming timing : timings) {
            if (timing.getStartTime() == null || timing.getEndTime() == null) {
                continue;
            }
            SlotConfig slotConfig = slotConfigRepository.findByClinicId(timing.getClinic().getId()).orElse(null);
            if (slotConfig == null) {
                continue;
            }

            for (TimeRange range : buildRanges(timing)) {
                createSlotsForRange(date, timing.getClinic(), slotConfig, range.start(), range.end());
            }
        }
    }

    @Transactional
    public int generateSlotsForRange(LocalDate fromDateInclusive, LocalDate toDateInclusive) {
        if (toDateInclusive.isBefore(fromDateInclusive)) {
            return 0;
        }
        long days = fromDateInclusive.datesUntil(toDateInclusive.plusDays(1)).count();
        if (days > maxDaysPerRequest) {
            throw new BadRequestException("Date range too large for slot generation");
        }

        int generatedDays = 0;
        LocalDate date = fromDateInclusive;
        while (!date.isAfter(toDateInclusive)) {
            generateSlotsForDate(date);
            generatedDays++;
            date = date.plusDays(1);
        }
        return generatedDays;
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotResponse> getSlots(LocalDate date, int page, int size) {
        bookingWindowValidator.validateDate(date);
        paginationValidator.validate(page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").ascending().and(Sort.by("id").ascending()));
        Page<SlotResponse> mapped = slotRepository.findBySlotDate(date, pageable).map(slotMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional(readOnly = true)
    public SlotConfigResponse getSlotConfig(Long doctorId, Long clinicId) {
        Clinic clinic = clinicRepository.findByIdAndDoctorId(clinicId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));
        SlotConfig config = slotConfigRepository.findByClinicId(clinic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot config not found"));
        return toSlotConfigResponse(config);
    }

    @Transactional
    public SlotConfigResponse updateSlotConfig(
            Long doctorId,
            Long clinicId,
            LocalDate effectiveDate,
            SlotConfigUpdateRequest request
    ) {
        Clinic clinic = clinicRepository.findByIdAndDoctorId(clinicId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        SlotConfig config = slotConfigRepository.findByClinicId(clinic.getId())
                .orElseGet(() -> {
                    SlotConfig slotConfig = new SlotConfig();
                    slotConfig.setClinic(clinic);
                    return slotConfig;
                });
        Integer slotDuration = request.getSlotDurationMinutes();
        if (slotDuration != null) {
            config.setSlotDurationMinutes(slotDuration);
        } else if (config.getSlotDurationMinutes() == null) {
            config.setSlotDurationMinutes(DEFAULT_SLOT_DURATION_MINUTES);
        }
        config.setMaxPatientsPerSlot(request.getMaxPatientsPerSlot());
        config = slotConfigRepository.save(config);

        applyCapacityFromDate(clinic.getId(), effectiveDate, request.getMaxPatientsPerSlot());

        auditLogService.logEvent(
                "SLOT_CONFIG_UPDATED",
                "doctor:" + doctorId,
                "CLINIC",
                clinicId.toString(),
                Map.of(
                        "slotDurationMinutes", config.getSlotDurationMinutes(),
                        "maxPatientsPerSlot", config.getMaxPatientsPerSlot()));

        return toSlotConfigResponse(config);
    }

    @Transactional
    public MessageResponse updateSlotCapacity(Long doctorId, Long clinicId, LocalDate effectiveDate, Integer maxPatientsPerSlot) {
        Clinic clinic = clinicRepository.findByIdAndDoctorId(clinicId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        SlotConfig config = slotConfigRepository.findByClinicId(clinic.getId())
                .orElseGet(() -> {
                    SlotConfig slotConfig = new SlotConfig();
                    slotConfig.setClinic(clinic);
                    slotConfig.setSlotDurationMinutes(DEFAULT_SLOT_DURATION_MINUTES);
                    return slotConfig;
                });

        if (config.getSlotDurationMinutes() == null) {
            config.setSlotDurationMinutes(DEFAULT_SLOT_DURATION_MINUTES);
        }
        config.setMaxPatientsPerSlot(maxPatientsPerSlot);
        slotConfigRepository.save(config);

        LocalDate fromDate = effectiveDate == null ? appTime.today() : effectiveDate;
        int updated = applyCapacityFromDate(clinic.getId(), fromDate, maxPatientsPerSlot);

        auditLogService.logEvent(
                "SLOT_CAPACITY_UPDATED",
                "doctor:" + doctorId,
                "CLINIC",
                clinicId.toString(),
                Map.of("maxPatientsPerSlot", maxPatientsPerSlot, "effectiveDate", fromDate.toString(), "updatedSlots", updated));

        return new MessageResponse(
                "Capacity updated to " + maxPatientsPerSlot + " for " + updated + " slot(s) from " + fromDate + " onward");
    }

    @Transactional
    public SlotResponse setSlotBlocked(Long doctorId, Long slotId, boolean blocked) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        if (!slot.getClinic().getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedException("You are not allowed to block this slot");
        }
        if (blocked && slot.getBookedCount() > 0) {
            throw new BadRequestException("Cannot block a slot that already has bookings");
        }

        slot.setIsBlocked(blocked);
        slot = slotRepository.save(slot);

        auditLogService.logEvent(
                blocked ? "SLOT_BLOCKED" : "SLOT_UNBLOCKED",
                "doctor:" + doctorId,
                "SLOT",
                slotId.toString(),
                Map.of("slotDate", slot.getSlotDate().toString(), "startTime", slot.getStartTime().toString()));

        return slotMapper.toResponse(slot);
    }

    @Transactional
    public MessageResponse setDateBlocked(Long doctorId, Long clinicId, LocalDate date, boolean blocked) {
        bookingWindowValidator.validateDate(date);
        Clinic clinic = clinicRepository.findByIdAndDoctorId(clinicId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        if (blocked) {
            long booked = slotRepository.countBookedSlotsOnDate(clinic.getId(), date);
            if (booked > 0) {
                throw new BadRequestException("Cannot block date with existing booked slots");
            }
        }

        int updated = slotRepository.updateBlockedByClinicIdAndSlotDate(clinic.getId(), date, blocked);
        if (updated == 0) {
            throw new BadRequestException("No slots found for selected date");
        }

        String action = blocked ? "blocked" : "unblocked";
        auditLogService.logEvent(
                blocked ? "DATE_BLOCKED" : "DATE_UNBLOCKED",
                "doctor:" + doctorId,
                "CLINIC",
                clinicId.toString(),
                Map.of("date", date.toString(), "updatedSlots", updated));

        return new MessageResponse("Successfully " + action + " " + updated + " slots for " + date);
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotDateAvailabilityResponse> getDateAvailability(
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        paginationValidator.validate(page, size);

        LocalDate today = appTime.today();
        LocalDate from = fromDate == null ? today : fromDate;
        LocalDate to = toDate == null ? today.plusMonths(BookingWindowValidator.MAX_ADVANCE_MONTHS) : toDate;

        bookingWindowValidator.validateDate(from);
        bookingWindowValidator.validateDate(to);
        if (to.isBefore(from)) {
            throw new BadRequestException("toDate must be after or equal to fromDate");
        }

        long days = from.datesUntil(to.plusDays(1)).count();
        if (days > maxDaysPerRequest) {
            throw new BadRequestException("Date range too large");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<SlotDateAvailabilityResponse> mapped = slotRepository.findDateAvailability(from, to, pageable)
                .map(this::toDateAvailabilityResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    private SlotDateAvailabilityResponse toDateAvailabilityResponse(DateAvailabilityProjection projection) {
        return SlotDateAvailabilityResponse.builder()
                .date(projection.getDate())
                .totalSlots(projection.getTotalSlots() == null ? 0 : projection.getTotalSlots().intValue())
                .slotsLeft(projection.getSlotsLeft() == null ? 0 : projection.getSlotsLeft().intValue())
                .build();
    }

    private java.util.List<TimeRange> buildRanges(ClinicTiming timing) {
        LocalTime start = timing.getStartTime();
        LocalTime end = timing.getEndTime();
        LocalTime breakStart = timing.getBreakStartTime();
        LocalTime breakEnd = timing.getBreakEndTime();
        var ranges = new ArrayList<TimeRange>();

        boolean hasValidBreak = breakStart != null
                && breakEnd != null
                && breakStart.isAfter(start)
                && breakEnd.isBefore(end)
                && breakEnd.isAfter(breakStart);

        if (!hasValidBreak) {
            ranges.add(new TimeRange(start, end));
            return ranges;
        }

        ranges.add(new TimeRange(start, breakStart));
        ranges.add(new TimeRange(breakEnd, end));
        return ranges;
    }

    private void createSlotsForRange(LocalDate date, Clinic clinic, SlotConfig config, LocalTime rangeStart, LocalTime rangeEnd) {
        int slotDuration = config.getSlotDurationMinutes();
        LocalTime current = rangeStart;

        while (!current.plusMinutes(slotDuration).isAfter(rangeEnd)) {
            LocalTime slotEnd = current.plusMinutes(slotDuration);
            slotRepository.insertIfAbsent(clinic.getId(), date, current, slotEnd, config.getMaxPatientsPerSlot());
            current = slotEnd;
        }
    }

    private int applyCapacityFromDate(Long clinicId, LocalDate effectiveDate, Integer capacity) {
        LocalDate today = appTime.today();
        LocalDate fromDate = effectiveDate == null ? today : effectiveDate;
        bookingWindowValidator.validateDate(fromDate);

        LocalTime fromTime = fromDate.equals(today) ? appTime.nowDateTime().toLocalTime() : LocalTime.MIN;

        long conflicts = slotRepository.countConflictsForDateFromTime(clinicId, fromDate, fromTime, capacity)
                + slotRepository.countConflictsAfterDate(clinicId, fromDate, capacity);
        if (conflicts > 0) {
            throw new BadRequestException(
                    "Cannot reduce capacity below already booked count for " + conflicts + " slot(s)");
        }

        int updatedToday = slotRepository.updateCapacityForDateFromTime(clinicId, fromDate, fromTime, capacity);
        int updatedFuture = slotRepository.updateCapacityAfterDate(clinicId, fromDate, capacity);
        return updatedToday + updatedFuture;
    }

    private record TimeRange(LocalTime start, LocalTime end) {
    }

    private SlotConfigResponse toSlotConfigResponse(SlotConfig config) {
        return SlotConfigResponse.builder()
                .id(config.getId())
                .clinicId(config.getClinic().getId())
                .slotDurationMinutes(config.getSlotDurationMinutes())
                .maxPatientsPerSlot(config.getMaxPatientsPerSlot())
                .build();
    }
}
