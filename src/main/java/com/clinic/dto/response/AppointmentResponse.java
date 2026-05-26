package com.clinic.dto.response;

import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentResponse {
    private Long id;
    private Long patientId;
    private String patientName;
    private String patientPhone;
    private Integer patientAge;
    private Gender patientGender;
    private Long slotId;
    private Long clinicId;
    private String clinicName;
    private String doctorName;
    private LocalDate slotDate;
    private LocalTime slotStartTime;
    private LocalTime slotEndTime;
    private String symptoms;
    private AppointmentStatus status;
    private LocalDateTime visitedAt;
    private String testimonial;
    private Integer rating;
    private LocalDateTime createdAt;
}
