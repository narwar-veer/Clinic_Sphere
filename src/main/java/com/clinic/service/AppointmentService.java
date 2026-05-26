package com.clinic.service;

import com.clinic.dto.request.AdminAppointmentUpdateRequest;
import com.clinic.dto.request.AppointmentBookingRequest;
import com.clinic.dto.request.AppointmentTestimonialRequest;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.Slot;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.SlotFullException;
import com.clinic.exception.UnauthorizedException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final SlotRepository slotRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final NotificationService notificationService;
    private final PageResponseFactory pageResponseFactory;
    private final BookingWindowValidator bookingWindowValidator;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentBookingRequest request) {
        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        bookingWindowValidator.validateSlotDateTime(slot.getSlotDate(), slot.getStartTime());

        if (Boolean.TRUE.equals(slot.getIsBlocked())) {
            throw new BadRequestException("Selected slot is blocked");
        }
        if (slot.getBookedCount() >= slot.getMaxPatients()) {
            throw new SlotFullException("Selected slot is fully booked");
        }

        Patient patient = patientRepository.findByPhoneForUpdate(request.getPhone()).orElse(null);
        if (patient != null) {
            ensureNoDuplicateBookingForDate(patient.getId(), slot.getSlotDate());
            patient = updatePatient(patient, request);
        } else {
            patient = createPatient(request);
        }
        patient = patientRepository.save(patient);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setSymptoms(request.getSymptoms());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment = appointmentRepository.save(appointment);

        slot.setBookedCount(slot.getBookedCount() + 1);
        slotRepository.save(slot);

        Long appointmentId = appointment.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.sendAppointmentConfirmationAsync(appointmentId);
            }
        });
        return appointmentMapper.toResponse(appointment);
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var specification = AppointmentSpecifications.forDoctorAppointments(doctorId, date, time, name, phone, status, visited);
        Page<AppointmentResponse> mapped = appointmentRepository.findAll(specification, pageable).map(appointmentMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long doctorId, Long appointmentId, AdminAppointmentUpdateRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        if (!appointment.getSlot().getClinic().getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedException("You are not allowed to update this appointment");
        }
        AppointmentStatus previousStatus = appointment.getStatus();
        AppointmentStatus nextStatus = request.getStatus();

        if (previousStatus == nextStatus) {
            return appointmentMapper.toResponse(appointment);
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
            appointment.setVisitedAt(LocalDateTime.now());
        } else if (nextStatus != AppointmentStatus.VISITED) {
            appointment.setVisitedAt(null);
        }
        slotRepository.save(slot);
        appointment = appointmentRepository.save(appointment);
        return appointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse submitTestimonial(Long appointmentId, AppointmentTestimonialRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        AppointmentStatus status = appointment.getStatus();
        if (status != AppointmentStatus.VISITED) {
            throw new BadRequestException("Testimonial can be submitted only for visited appointments");
        }

        appointment.setTestimonial(request.getTestimonial().trim());
        appointment.setRating(request.getRating());
        appointment = appointmentRepository.save(appointment);
        return appointmentMapper.toResponse(appointment);
    }

    private Patient updatePatient(Patient patient, AppointmentBookingRequest request) {
        patient.setName(request.getName());
        patient.setAge(request.getAge());
        patient.setGender(request.getGender());
        return patient;
    }

    private Patient createPatient(AppointmentBookingRequest request) {
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setAge(request.getAge());
        patient.setGender(request.getGender());
        return patient;
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
}
