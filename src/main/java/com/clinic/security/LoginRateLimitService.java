package com.clinic.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String key, int maxAttempts, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count != null && count <= maxAttempts;
        } catch (Exception ex) {
            log.error("Login rate-limit redis failure key={}", key, ex);
            return true;
        }
    }
}