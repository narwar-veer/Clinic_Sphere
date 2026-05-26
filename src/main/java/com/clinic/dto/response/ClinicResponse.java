package com.clinic.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClinicResponse {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private String clinicName;
    private String address;
    private String phone;
    private String whatsapp;
    private String email;
    private String mapLocation;
}
