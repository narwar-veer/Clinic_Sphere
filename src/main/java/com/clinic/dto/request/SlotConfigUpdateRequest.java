package com.clinic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlotConfigUpdateRequest {

    @Min(1)
    private Integer slotDurationMinutes;

    @NotNull
    @Min(1)
    private Integer maxPatientsPerSlot;
}
