package com.clinic.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MedicalRecordCreateRequest {

    @NotNull
    private Long appointmentId;

    @NotBlank
    private String diagnosis;

    private String prescriptionNotes;

    @JsonAlias("referredBy")
    @JsonProperty("referred_by")
    private String referredBy;
}
