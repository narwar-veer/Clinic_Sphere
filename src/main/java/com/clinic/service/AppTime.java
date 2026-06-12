package com.clinic.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppTime {

    private final Clock appClock;
    private final ZoneId appZoneId;

    public Instant nowInstant() {
        return Instant.now(appClock);
    }

    public LocalDateTime nowDateTime() {
        return LocalDateTime.now(appClock);
    }

    public LocalDate today() {
        return LocalDate.now(appClock);
    }

    public ZoneId zoneId() {
        return appZoneId;
    }

    public Clock clock() {
        return appClock;
    }
}