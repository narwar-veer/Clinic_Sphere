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
import com.clinic.repository.SlotConfigRepository;
import com.clinic.repository.SlotRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    @Transactional
    public void generateSlotsForDate(LocalDate date) {
        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        List<ClinicTiming> timings = clinicTimingRepository.findByDayOfWeekAndIsClosedFalse(dayOfWeek);

        for (ClinicTiming timing : timings) {
            if (timing.getStartTime() == null || timing.getEndTime() == null) {
                continue;
            }
            SlotConfig slotConfig = slotConfigRepository.findByClinicId(timing.getClinic().getId()).orElse(null);
            if (slotConfig == null) {
                continue;
            }
            List<TimeRange> ranges = buildRanges(timing);
            for (TimeRange range : ranges) {
                createSlotsForRange(date, timing.getClinic(), slotConfig, range.start(), range.end());
            }
        }
    }

    @Transactional
    public PageResponse<SlotResponse> getSlots(LocalDate date, int page, int size) {
        bookingWindowValidator.validateDate(date);
        generateSlotsForDate(date);
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

        LocalDate fromDate = effectiveDate == null ? LocalDate.now() : effectiveDate;
        int updated = applyCapacityFromDate(clinic.getId(), fromDate, maxPatientsPerSlot);
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
        slot.setIsBlocked(blocked);
        slot = slotRepository.save(slot);
        return slotMapper.toResponse(slot);
    }

    @Transactional
    public MessageResponse setDateBlocked(Long doctorId, Long clinicId, LocalDate date, boolean blocked) {
        bookingWindowValidator.validateDate(date);
        Clinic clinic = clinicRepository.findByIdAndDoctorId(clinicId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        generateSlotsForDate(date);
        int updated = slotRepository.updateBlockedByClinicIdAndSlotDate(clinic.getId(), date, blocked);
        if (updated == 0) {
            throw new BadRequestException("No slots found for selected date");
        }
        String action = blocked ? "blocked" : "unblocked";
        return new MessageResponse("Successfully " + action + " " + updated + " slots for " + date);
    }

    @Transactional
    public PageResponse<SlotDateAvailabilityResponse> getDateAvailability(
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        LocalDate today = LocalDate.now();
        LocalDate from = fromDate == null ? today : fromDate;
        LocalDate to = toDate == null ? today.plusMonths(BookingWindowValidator.MAX_ADVANCE_MONTHS) : toDate;

        bookingWindowValidator.validateDate(from);
        bookingWindowValidator.validateDate(to);
        if (to.isBefore(from)) {
            throw new BadRequestException("toDate must be after or equal to fromDate");
        }

        List<LocalDate> dates = from.datesUntil(to.plusDays(1)).toList();
        for (LocalDate date : dates) {
            generateSlotsForDate(date);
        }

        List<Slot> slots = slotRepository.findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(from, to);
        Map<LocalDate, MutableDateAvailability> availabilityByDate = new LinkedHashMap<>();
        for (LocalDate date : dates) {
            availabilityByDate.put(date, new MutableDateAvailability(date));
        }

        for (Slot slot : slots) {
            MutableDateAvailability availability = availabilityByDate.get(slot.getSlotDate());
            if (availability == null) {
                continue;
            }
            availability.totalSlots++;
            availability.slotsLeft += slot.getMaxPatients() - slot.getBookedCount();
        }

        List<SlotDateAvailabilityResponse> aggregated = availabilityByDate.values().stream()
                .map(value -> SlotDateAvailabilityResponse.builder()
                        .date(value.date)
                        .totalSlots(value.totalSlots)
                        .slotsLeft(value.slotsLeft)
                        .build())
                .toList();

        int fromIndex = Math.min(page * size, aggregated.size());
        int toIndex = Math.min(fromIndex + size, aggregated.size());
        List<SlotDateAvailabilityResponse> content = aggregated.subList(fromIndex, toIndex);

        Page<SlotDateAvailabilityResponse> pageResult = new PageImpl<>(content, PageRequest.of(page, size), aggregated.size());
        return pageResponseFactory.fromPage(pageResult);
    }

    private List<TimeRange> buildRanges(ClinicTiming timing) {
        LocalTime start = timing.getStartTime();
        LocalTime end = timing.getEndTime();
        LocalTime breakStart = timing.getBreakStartTime();
        LocalTime breakEnd = timing.getBreakEndTime();
        List<TimeRange> ranges = new ArrayList<>();

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
            tryCreateSlot(date, clinic, config, current, slotEnd);
            current = slotEnd;
        }
    }

    private void tryCreateSlot(LocalDate date, Clinic clinic, SlotConfig config, LocalTime startTime, LocalTime endTime) {
        if (slotRepository.existsByClinicIdAndSlotDateAndStartTime(clinic.getId(), date, startTime)) {
            return;
        }

        Slot slot = new Slot();
        slot.setClinic(clinic);
        slot.setSlotDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setMaxPatients(config.getMaxPatientsPerSlot());
        slot.setBookedCount(0);
        slot.setIsBlocked(false);
        try {
            slotRepository.save(slot);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Ignoring duplicate slot generation for clinicId={} date={} startTime={}", clinic.getId(), date, startTime);
        }
    }

    private int applyCapacityFromDate(Long clinicId, LocalDate effectiveDate, Integer capacity) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = effectiveDate == null ? today : effectiveDate;
        bookingWindowValidator.validateDate(fromDate);

        // Ensure target day slots exist before applying updated capacity.
        generateSlotsForDate(fromDate);

        LocalTime fromTime = fromDate.equals(today) ? LocalTime.now() : LocalTime.MIN;

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

    private static class MutableDateAvailability {
        private final LocalDate date;
        private int totalSlots;
        private int slotsLeft;

        private MutableDateAvailability(LocalDate date) {
            this.date = date;
        }
    }
}
