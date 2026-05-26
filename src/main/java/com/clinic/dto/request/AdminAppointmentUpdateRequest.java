package com.clinic.dto.request;

import com.clinic.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminAppointmentUpdateRequest {

    @NotNull
    private AppointmentStatus status;
}
