package com.clinic.service;

import com.clinic.dto.request.MedicalRecordCreateRequest;
import com.clinic.dto.response.MedicalRecordResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.MedicalRecord;
import com.clinic.entity.Patient;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.UnauthorizedException;
import com.clinic.mapper.MedicalRecordMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.MedicalRecordRepository;
import com.clinic.repository.PatientRepository;
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
public class PatientHistoryService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordMapper medicalRecordMapper;
    private final PageResponseFactory pageResponseFactory;
    private final PaginationValidator paginationValidator;
    private final AuditLogService auditLogService;
    private final AppTime appTime;

    @Transactional(readOnly = true)
    public PageResponse<MedicalRecordResponse> getPatientHistory(Long patientId, Long adminDoctorId, int page, int size) {
        paginationValidator.validate(page, size);

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        if (!appointmentRepository.existsByPatientIdAndDoctorId(patientId, adminDoctorId)) {
            throw new UnauthorizedException("You are not allowed to view this patient history");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MedicalRecordResponse> mapped = medicalRecordRepository
                .findByPatientIdOrderByCreatedAtDesc(patientId, pageable)
                .map(medicalRecordMapper::toResponse);

        auditLogService.logEvent(
                "PATIENT_HISTORY_VIEWED",
                "doctor:" + adminDoctorId,
                "PATIENT",
                patientId.toString(),
                Map.of("page", page, "size", size));

        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional
    public MedicalRecordResponse addMedicalRecord(Long patientId, Long adminDoctorId, MedicalRecordCreateRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        Appointment appointment = appointmentRepository.findByIdForUpdate(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new BadRequestException("Appointment does not belong to this patient");
        }

        Long appointmentDoctorId = appointment.getSlot().getClinic().getDoctor().getId();
        if (!appointmentDoctorId.equals(adminDoctorId)) {
            throw new UnauthorizedException("You are not allowed to add record for this appointment");
        }

        MedicalRecord medicalRecord;
        try {
            if (medicalRecordRepository.existsByAppointmentId(appointment.getId())) {
                throw new BadRequestException("Medical record already exists for this appointment");
            }
            medicalRecord = new MedicalRecord();
            medicalRecord.setPatient(patient);
            medicalRecord.setDoctor(appointment.getSlot().getClinic().getDoctor());
            medicalRecord.setAppointment(appointment);
            medicalRecord.setDiagnosis(request.getDiagnosis().trim());
            medicalRecord.setPrescriptionNotes(trimToNull(request.getPrescriptionNotes()));
            medicalRecord.setReferredBy(trimToNull(request.getReferredBy()));
            medicalRecord = medicalRecordRepository.saveAndFlush(medicalRecord);
        } catch (DataIntegrityViolationException ex) {
            medicalRecord = medicalRecordRepository.findByAppointmentId(appointment.getId())
                    .orElseThrow(() -> new ConflictException("Medical record already exists"));
        }

        if (appointment.getStatus() != AppointmentStatus.VISITED) {
            appointment.setStatus(AppointmentStatus.VISITED);
            if (appointment.getVisitedAt() == null) {
                appointment.setVisitedAt(appTime.nowDateTime());
            }
            appointmentRepository.save(appointment);
        }

        auditLogService.logEvent(
                "MEDICAL_RECORD_CREATED",
                "doctor:" + adminDoctorId,
                "MEDICAL_RECORD",
                medicalRecord.getId().toString(),
                Map.of("patientId", patientId, "appointmentId", appointment.getId()));

        return medicalRecordMapper.toResponse(medicalRecord);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
