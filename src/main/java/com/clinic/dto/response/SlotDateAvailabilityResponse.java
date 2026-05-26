package com.clinic.dto.response;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SlotDateAvailabilityResponse {
    private LocalDate date;
    private int totalSlots;
    private int slotsLeft;
}
