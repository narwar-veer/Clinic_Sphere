package com.clinic.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public ZoneId appZoneId(@Value("${app.time-zone:Asia/Kolkata}") String zoneId) {
        return ZoneId.of(zoneId);
    }

    @Bean
    public Clock appClock(ZoneId appZoneId) {
        return Clock.system(appZoneId);
    }
}