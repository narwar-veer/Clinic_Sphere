package com.clinic.controller;

import com.clinic.dto.request.MedicalRecordCreateRequest;
import com.clinic.dto.response.MedicalRecordResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.service.AdminService;
import com.clinic.service.PatientHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/patients/{id}/history")
@RequiredArgsConstructor
public class PatientHistoryController {

    private final PatientHistoryService patientHistoryService;
    private final AdminService adminService;

    @GetMapping
    public PageResponse<MedicalRecordResponse> getHistory(
            @PathVariable("id") Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return patientHistoryService.getPatientHistory(patientId, page, size);
    }

    @PostMapping
    public MedicalRecordResponse addHistory(
            @PathVariable("id") Long patientId,
            @Valid @RequestBody MedicalRecordCreateRequest request
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return patientHistoryService.addMedicalRecord(patientId, doctorId, request);
    }
}
