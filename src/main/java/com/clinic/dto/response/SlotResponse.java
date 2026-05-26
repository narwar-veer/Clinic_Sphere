package com.clinic.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlotResponse {
    private Long id;
    private Long clinicId;
    private String clinicName;
    private Long doctorId;
    private String doctorName;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxPatients;
    private Integer bookedCount;
    private Boolean blocked;
    private Integer availableCapacity;
}
