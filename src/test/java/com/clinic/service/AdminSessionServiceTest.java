package com.clinic.service;

import com.clinic.entity.AdminSession;
import com.clinic.entity.Doctor;
import com.clinic.repository.AdminSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AdminSessionServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");

    @Test
    void registerSession_persistsSessionWhenRedisCacheIsUnavailable() {
        AdminSessionRepository repository = Mockito.mock(AdminSessionRepository.class);
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AdminSessionService service = createService(repository, entityManager, redisTemplate);

        Doctor doctor = new Doctor();
        doctor.setId(99L);
        Mockito.when(entityManager.getReference(Doctor.class, 99L)).thenReturn(doctor);
        Mockito.when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("down"));

        LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZONE_ID);
        Assertions.assertDoesNotThrow(() ->
                service.registerSession("token-1", "admin", 99L, expiresAt));

        ArgumentCaptor<AdminSession> captor = ArgumentCaptor.forClass(AdminSession.class);
        Mockito.verify(repository).save(captor.capture());
        AdminSession saved = captor.getValue();
        Assertions.assertEquals("token-1", saved.getTokenId());
        Assertions.assertEquals("admin", saved.getUsername());
        Assertions.assertEquals(doctor, saved.getDoctor());
        Assertions.assertEquals(expiresAt, saved.getExpiresAt());
    }

    @Test
    void validateSession_usesDatabaseWhenRedisCacheIsUnavailable() {
        AdminSessionRepository repository = Mockito.mock(AdminSessionRepository.class);
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        AdminSessionService service = createService(repository, entityManager, redisTemplate);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZONE_ID);
        AdminSession session = new AdminSession();
        session.setTokenId("token-1");
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plusHours(1));

        Mockito.when(redisTemplate.hasKey("session:revoked:token-1"))
                .thenThrow(new RedisConnectionFailureException("down"));
        Mockito.when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("down"));
        Mockito.when(repository.findActiveById("token-1", now)).thenReturn(Optional.of(session));

        boolean valid = service.validateSession("token-1", NOW.plusSeconds(3600));

        Assertions.assertTrue(valid);
        Mockito.verify(repository).findActiveById("token-1", now);
    }

    private static AdminSessionService createService(AdminSessionRepository repository,
                                                     EntityManager entityManager,
                                                     StringRedisTemplate redisTemplate) {
        Clock clock = Clock.fixed(NOW, ZONE_ID);
        AdminSessionService service = new AdminSessionService(
                repository,
                entityManager,
                redisTemplate,
                new AppTime(clock, ZONE_ID));
        ReflectionTestUtils.setField(service, "inactivityTimeoutMinutes", 30L);
        ReflectionTestUtils.setField(service, "touchThrottleSeconds", 120L);
        return service;
    }
}
