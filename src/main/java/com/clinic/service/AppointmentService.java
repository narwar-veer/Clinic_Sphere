package com.clinic.service;

import com.clinic.dto.request.AdminAppointmentUpdateRequest;
import com.clinic.dto.request.AppointmentBookingRequest;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.Slot;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.SlotFullException;
import com.clinic.exception.UnauthorizedException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final SlotRepository slotRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final PageResponseFactory pageResponseFactory;
    private final BookingWindowValidator bookingWindowValidator;
    private final PaginationValidator paginationValidator;
    private final OutboxService outboxService;
    private final AuditLogService auditLogService;
    private final AppTime appTime;
    private final MeterRegistry meterRegistry;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentBookingRequest request, String idempotencyKey) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
            if (normalizedIdempotencyKey != null) {
                Appointment existing = appointmentRepository.findByIdempotencyKey(normalizedIdempotencyKey).orElse(null);
                if (existing != null) {
                    return appointmentMapper.toResponse(existing);
                }
            }

            Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                    .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
            bookingWindowValidator.validateSlotDateTime(slot.getSlotDate(), slot.getStartTime());

            if (Boolean.TRUE.equals(slot.getIsBlocked())) {
                throw new BadRequestException("Selected slot is blocked");
            }
            if (slot.getBookedCount() >= slot.getMaxPatients()) {
                throw new SlotFullException("Selected slot is fully booked");
            }

            Patient patient = findOrCreatePatient(request);
            ensureNoDuplicateBookingForDate(patient.getId(), slot.getSlotDate());

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setSlot(slot);
            appointment.setSymptoms(trimToNull(request.getSymptoms()));
            appointment.setStatus(AppointmentStatus.BOOKED);
            appointment.setBookingDate(slot.getSlotDate());
            appointment.setIdempotencyKey(normalizedIdempotencyKey);

            appointment = appointmentRepository.saveAndFlush(appointment);

            slot.setBookedCount(slot.getBookedCount() + 1);
            slotRepository.save(slot);

            String outboxKey = outboxService.enqueue(
                    "APPOINTMENT",
                    appointment.getId().toString(),
                    "APPOINTMENT_CONFIRMATION",
                    new AppointmentNotificationOutboxPayload(appointment.getId()));

            auditLogService.logEvent(
                    "APPOINTMENT_BOOKED",
                    "public",
                    "APPOINTMENT",
                    appointment.getId().toString(),
                    Map.of(
                            "slotId", slot.getId(),
                            "patientId", patient.getId(),
                            "outboxKey", outboxKey));

            return appointmentMapper.toResponse(appointment);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Concurrent booking conflict. Please retry");
        } finally {
            timer.stop(Timer.builder("clinic.booking.latency").register(meterRegistry));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getAdminAppointments(
            Long doctorId,
            int page,
            int size,
            LocalDate date,
            LocalTime time,
            String name,
            String phone,
            AppointmentStatus status,
            Boolean visited
    ) {
        paginationValidator.validate(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var specification = AppointmentSpecifications.forDoctorAppointments(doctorId, date, time, name, phone, status, visited);
        Page<AppointmentResponse> mapped = appointmentRepository.findAll(specification, pageable).map(appointmentMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long doctorId, Long appointmentId, AdminAppointmentUpdateRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        if (!appointment.getSlot().getClinic().getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedException("You are not allowed to update this appointment");
        }

        AppointmentStatus previousStatus = appointment.getStatus();
        AppointmentStatus nextStatus = request.getStatus();

        if (previousStatus == nextStatus) {
            return appointmentMapper.toResponse(appointment);
        }

        if (previousStatus == AppointmentStatus.VISITED && nextStatus != AppointmentStatus.VISITED) {
            throw new BadRequestException("Visited appointment status cannot be changed");
        }

        Slot slot = slotRepository.findByIdForUpdate(appointment.getSlot().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found for appointment"));

        if (previousStatus != AppointmentStatus.CANCELLED && nextStatus == AppointmentStatus.CANCELLED) {
            if (slot.getBookedCount() > 0) {
                slot.setBookedCount(slot.getBookedCount() - 1);
            }
        } else if (previousStatus == AppointmentStatus.CANCELLED && nextStatus != AppointmentStatus.CANCELLED) {
            if (slot.getBookedCount() >= slot.getMaxPatients()) {
                throw new SlotFullException("Cannot restore appointment because slot is full");
            }
            slot.setBookedCount(slot.getBookedCount() + 1);
        }

        appointment.setStatus(nextStatus);
        if (nextStatus == AppointmentStatus.VISITED && appointment.getVisitedAt() == null) {
            appointment.setVisitedAt(appTime.nowDateTime());
        }

        slotRepository.save(slot);
        appointment = appointmentRepository.save(appointment);

        auditLogService.logEvent(
                "APPOINTMENT_STATUS_CHANGED",
                "doctor:" + doctorId,
                "APPOINTMENT",
                appointment.getId().toString(),
                Map.of("from", previousStatus.name(), "to", nextStatus.name()));

        return appointmentMapper.toResponse(appointment);
    }

    private Patient findOrCreatePatient(AppointmentBookingRequest request) {
        String normalizedName = request.getName().trim();
        String normalizedPhone = request.getPhone().trim();

        return patientRepository.findByIdentityForUpdate(normalizedPhone, normalizedName, request.getAge(), request.getGender())
                .orElseGet(() -> {
                    Patient patient = new Patient();
                    patient.setName(normalizedName);
                    patient.setPhone(normalizedPhone);
                    patient.setAge(request.getAge());
                    patient.setGender(request.getGender());
                    try {
                        return patientRepository.saveAndFlush(patient);
                    } catch (DataIntegrityViolationException ex) {
                        return patientRepository.findFirstByPhoneAndNameIgnoreCaseAndAgeAndGenderOrderByCreatedAtDesc(
                                        normalizedPhone,
                                        normalizedName,
                                        request.getAge(),
                                        request.getGender())
                                .orElseThrow(() -> new ConflictException("Concurrent patient creation conflict"));
                    }
                });
    }

    private void ensureNoDuplicateBookingForDate(Long patientId, LocalDate slotDate) {
        appointmentRepository
                .findFirstByPatientIdAndSlotSlotDateAndStatusNotOrderBySlotStartTimeAsc(
                        patientId,
                        slotDate,
                        AppointmentStatus.CANCELLED
                )
                .ifPresent(existing -> {
                    String date = existing.getSlot().getSlotDate().format(DATE_FORMAT);
                    String start = existing.getSlot().getStartTime().format(TIME_FORMAT);
                    String end = existing.getSlot().getEndTime().format(TIME_FORMAT);
                    throw new BadRequestException(
                            "Appointment is already booked on " + date + " at " + start + " - " + end);
                });
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        String normalized = trimToNull(idempotencyKey);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 120) {
            throw new BadRequestException("X-Idempotency-Key too long");
        }
        return normalized;
    }
}
