package com.clinic.service;

import com.clinic.dto.request.MedicalRecordCreateRequest;
import com.clinic.dto.response.MedicalRecordResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.MedicalRecord;
import com.clinic.entity.Patient;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.exception.UnauthorizedException;
import com.clinic.mapper.MedicalRecordMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.MedicalRecordRepository;
import com.clinic.repository.PatientRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public PageResponse<MedicalRecordResponse> getPatientHistory(Long patientId, int page, int size) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MedicalRecordResponse> mapped = medicalRecordRepository
                .findByPatientIdOrderByCreatedAtDesc(patientId, pageable)
                .map(medicalRecordMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional
    public MedicalRecordResponse addMedicalRecord(Long patientId, Long adminDoctorId, MedicalRecordCreateRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new BadRequestException("Appointment does not belong to this patient");
        }
        if (medicalRecordRepository.existsByAppointmentId(appointment.getId())) {
            throw new BadRequestException("Medical record already exists for this appointment");
        }

        Long appointmentDoctorId = appointment.getSlot().getClinic().getDoctor().getId();
        if (!appointmentDoctorId.equals(adminDoctorId)) {
            throw new UnauthorizedException("You are not allowed to add record for this appointment");
        }

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setPatient(patient);
        medicalRecord.setDoctor(appointment.getSlot().getClinic().getDoctor());
        medicalRecord.setAppointment(appointment);
        medicalRecord.setDiagnosis(request.getDiagnosis());
        medicalRecord.setPrescriptionNotes(request.getPrescriptionNotes());
        medicalRecord.setReferredBy(request.getReferredBy());
        medicalRecord = medicalRecordRepository.save(medicalRecord);

        if (appointment.getStatus() != AppointmentStatus.VISITED) {
            appointment.setStatus(AppointmentStatus.VISITED);
            if (appointment.getVisitedAt() == null) {
                appointment.setVisitedAt(LocalDateTime.now());
            }
            appointmentRepository.save(appointment);
        }
        return medicalRecordMapper.toResponse(medicalRecord);
    }
}
