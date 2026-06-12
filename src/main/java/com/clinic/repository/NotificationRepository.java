package com.clinic.repository;

import com.clinic.entity.Notification;
import com.clinic.entity.NotificationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByTypeAndIdempotencyKey(NotificationType type, String idempotencyKey);
}