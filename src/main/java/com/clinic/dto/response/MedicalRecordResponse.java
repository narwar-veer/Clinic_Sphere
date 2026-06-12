package com.clinic.dto.response;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MedicalRecordResponse {
    private Long id;
    private Long patientId;
    private Long doctorId;
    private String doctorName;
    private Long appointmentId;
    private String diagnosis;
    private String prescriptionNotes;
    private String referredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
