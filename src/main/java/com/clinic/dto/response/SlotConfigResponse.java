package com.clinic.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SlotConfigResponse {
    private Long id;
    private Long clinicId;
    private Integer slotDurationMinutes;
    private Integer maxPatientsPerSlot;
}
