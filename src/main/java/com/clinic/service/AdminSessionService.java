package com.clinic.service;

import com.clinic.entity.AdminSession;
import com.clinic.entity.Doctor;
import com.clinic.repository.AdminSessionRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSessionService {

    private final AdminSessionRepository adminSessionRepository;
    private final EntityManager entityManager;

    @Value("${app.session.inactivity-timeout-minutes:30}")
    private long inactivityTimeoutMinutes;

    @Transactional
    public void registerSession(String tokenId, String username, Long doctorId, LocalDateTime expiresAt) {
        AdminSession session = new AdminSession();
        LocalDateTime now = LocalDateTime.now();
        session.setTokenId(tokenId);
        session.setUsername(username);
        session.setDoctor(entityManager.getReference(Doctor.class, doctorId));
        session.setLastActivityAt(now);
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(null);
        adminSessionRepository.save(session);
    }

    @Transactional
    public boolean validateAndTouchSession(String tokenId) {
        return adminSessionRepository.findById(tokenId)
                .map(session -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
                        return false;
                    }
                    if (session.getLastActivityAt().plusMinutes(inactivityTimeoutMinutes).isBefore(now)) {
                        session.setRevokedAt(now);
                        adminSessionRepository.save(session);
                        log.info("Session expired by inactivity tokenId={}", tokenId);
                        return false;
                    }
                    session.setLastActivityAt(now);
                    adminSessionRepository.save(session);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void revokeSession(String tokenId) {
        adminSessionRepository.findById(tokenId).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(LocalDateTime.now());
                adminSessionRepository.save(session);
            }
        });
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        int removed = adminSessionRepository.deleteExpiredSessions(LocalDateTime.now());
        if (removed > 0) {
            log.debug("Cleaned up {} expired admin session(s)", removed);
        }
    }
}
