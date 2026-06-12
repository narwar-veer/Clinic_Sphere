package com.clinic.service;

import com.clinic.entity.AdminSession;
import com.clinic.entity.Doctor;
import com.clinic.repository.AdminSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String REVOKED_KEY_PREFIX = "session:revoked:";

    private final AdminSessionRepository adminSessionRepository;
    private final EntityManager entityManager;
    private final StringRedisTemplate redisTemplate;
    private final AppTime appTime;

    @Value("${app.session.inactivity-timeout-minutes:30}")
    private long inactivityTimeoutMinutes;

    @Value("${app.session.touch-throttle-seconds:120}")
    private long touchThrottleSeconds;

    @Transactional
    public void registerSession(String tokenId, String username, Long doctorId, LocalDateTime expiresAt) {
        LocalDateTime now = appTime.nowDateTime();
        AdminSession session = new AdminSession();
        session.setTokenId(tokenId);
        session.setUsername(username);
        session.setDoctor(entityManager.getReference(Doctor.class, doctorId));
        session.setLastActivityAt(now);
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(null);
        adminSessionRepository.save(session);

        cacheSession(tokenId, now, expiresAt);
    }

    public boolean validateSession(String tokenId, Instant tokenExpiresAt) {
        LocalDateTime now = appTime.nowDateTime();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(tokenId)))) {
            return false;
        }

        String key = sessionKey(tokenId);
        String lastActivityRaw = redisTemplate.opsForHash().get(key, "lastActivityAt") instanceof String v ? v : null;
        String expiresAtRaw = redisTemplate.opsForHash().get(key, "expiresAt") instanceof String v ? v : null;

        if (lastActivityRaw == null || expiresAtRaw == null) {
            return hydrateFromDatabase(tokenId, now);
        }

        LocalDateTime lastActivity = LocalDateTime.parse(lastActivityRaw);
        LocalDateTime expiresAt = LocalDateTime.parse(expiresAtRaw);

        if (!expiresAt.isAfter(now) || !tokenExpiresAt.isAfter(appTime.nowInstant())) {
            revokeSession(tokenId);
            return false;
        }

        if (lastActivity.plusMinutes(inactivityTimeoutMinutes).isBefore(now)) {
            revokeSession(tokenId);
            log.info("Session expired by inactivity tokenId={}", tokenId);
            return false;
        }

        maybeTouchCache(key, lastActivity, now, expiresAt);
        return true;
    }

    @Transactional
    public void revokeSession(String tokenId) {
        LocalDateTime now = appTime.nowDateTime();
        adminSessionRepository.revokeIfActive(tokenId, now);

        String key = sessionKey(tokenId);
        String expiresAtRaw = redisTemplate.opsForHash().get(key, "expiresAt") instanceof String v ? v : null;
        if (expiresAtRaw != null) {
            LocalDateTime expiresAt = LocalDateTime.parse(expiresAtRaw);
            Duration ttl = Duration.between(now, expiresAt).plusMinutes(inactivityTimeoutMinutes);
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(revokedKey(tokenId), "1", ttl);
            }
        } else {
            redisTemplate.opsForValue().set(revokedKey(tokenId), "1", Duration.ofMinutes(inactivityTimeoutMinutes));
        }
        redisTemplate.delete(key);
    }

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = appTime.nowDateTime();
        int removed = adminSessionRepository.deleteExpiredOrRevokedSessions(now, now.minusDays(2));
        if (removed > 0) {
            log.info("Cleaned up {} expired/revoked admin session(s)", removed);
        }
    }

    private boolean hydrateFromDatabase(String tokenId, LocalDateTime now) {
        return adminSessionRepository.findActiveById(tokenId, now)
                .map(session -> {
                    if (session.getLastActivityAt().plusMinutes(inactivityTimeoutMinutes).isBefore(now)) {
                        revokeSession(tokenId);
                        return false;
                    }
                    cacheSession(tokenId, session.getLastActivityAt(), session.getExpiresAt());
                    return true;
                })
                .orElse(false);
    }

    private void maybeTouchCache(String key, LocalDateTime lastActivity, LocalDateTime now, LocalDateTime expiresAt) {
        if (lastActivity.plusSeconds(touchThrottleSeconds).isAfter(now)) {
            return;
        }
        redisTemplate.opsForHash().put(key, "lastActivityAt", now.toString());
        Duration ttl = Duration.between(now, expiresAt).plusMinutes(inactivityTimeoutMinutes);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.expire(key, ttl);
        }
    }

    private void cacheSession(String tokenId, LocalDateTime lastActivityAt, LocalDateTime expiresAt) {
        LocalDateTime now = appTime.nowDateTime();
        String key = sessionKey(tokenId);
        redisTemplate.opsForHash().put(key, "lastActivityAt", lastActivityAt.toString());
        redisTemplate.opsForHash().put(key, "expiresAt", expiresAt.toString());
        Duration ttl = Duration.between(now, expiresAt).plusMinutes(inactivityTimeoutMinutes);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.expire(key, ttl);
        }
    }

    private String sessionKey(String tokenId) {
        return SESSION_KEY_PREFIX + tokenId;
    }

    private String revokedKey(String tokenId) {
        return REVOKED_KEY_PREFIX + tokenId;
    }
}