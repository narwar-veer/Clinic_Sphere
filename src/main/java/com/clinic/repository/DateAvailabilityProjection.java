package com.clinic.repository;

import java.time.LocalDate;

public interface DateAvailabilityProjection {
    LocalDate getDate();
    Long getTotalSlots();
    Long getSlotsLeft();
}
