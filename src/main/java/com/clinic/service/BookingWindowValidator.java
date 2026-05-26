package com.clinic.service;

import com.clinic.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookingWindowValidator {

    public static final int MAX_ADVANCE_MONTHS = 1;
    private final ZoneId bookingZoneId;

    public BookingWindowValidator(@Value("${app.booking.time-zone:Asia/Kolkata}") String bookingTimeZone) {
        this.bookingZoneId = ZoneId.of(bookingTimeZone);
    }

    public void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(bookingZoneId);
        LocalDate maxBookableDate = today.plusMonths(MAX_ADVANCE_MONTHS);

        if (date.isBefore(today)) {
            throw new BadRequestException("Past date selection is not allowed");
        }
        if (date.isAfter(maxBookableDate)) {
            throw new BadRequestException("Advance booking is allowed only for the next 1 month");
        }
    }

    public void validateSlotDateTime(LocalDate slotDate, LocalTime slotStartTime) {
        validateDate(slotDate);
        LocalDateTime slotDateTime = LocalDateTime.of(slotDate, slotStartTime);
        if (slotDateTime.isBefore(LocalDateTime.now(bookingZoneId))) {
            throw new BadRequestException("Past date/time selection is not allowed");
        }
    }
}
