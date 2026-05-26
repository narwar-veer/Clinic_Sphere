package com.clinic.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoctorResponse {
    private Long id;
    private String name;
    private String photo;
    private String qualifications;
    private String degrees;
    private String specialization;
    private Integer experienceYears;
    private String registrationNumber;
    private String bio;
    private String awards;
}
