package com.clinic.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("referred_by")
    private String referredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
