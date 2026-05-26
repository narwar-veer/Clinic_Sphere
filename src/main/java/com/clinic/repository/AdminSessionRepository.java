package com.clinic.repository;

import com.clinic.entity.AdminSession;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {

    @Modifying
    @Query("delete from AdminSession s where s.expiresAt <= :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);
}
