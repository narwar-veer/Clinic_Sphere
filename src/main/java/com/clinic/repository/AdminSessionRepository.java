package com.clinic.repository;

import com.clinic.entity.AdminSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {

    @Query("""
            select s from AdminSession s
            where s.tokenId = :tokenId
              and s.revokedAt is null
              and s.expiresAt > :now
            """)
    Optional<AdminSession> findActiveById(@Param("tokenId") String tokenId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update AdminSession s
            set s.revokedAt = :revokedAt,
                s.lastActivityAt = :revokedAt
            where s.tokenId = :tokenId
              and s.revokedAt is null
            """)
    int revokeIfActive(@Param("tokenId") String tokenId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("delete from AdminSession s where s.expiresAt <= :now or s.revokedAt <= :revokedBefore")
    int deleteExpiredOrRevokedSessions(@Param("now") LocalDateTime now, @Param("revokedBefore") LocalDateTime revokedBefore);
}