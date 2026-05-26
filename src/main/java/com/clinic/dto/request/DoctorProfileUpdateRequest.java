package com.clinic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoctorProfileUpdateRequest {

    @NotBlank
    private String name;

    private String photo;

    @NotBlank
    private String qualifications;

    @NotBlank
    private String specialization;

    @NotNull
    @Min(0)
    @Max(80)
    private Integer experienceYears;

    @NotBlank
    private String registrationNumber;

    private String biography;

    private String awards;
}
