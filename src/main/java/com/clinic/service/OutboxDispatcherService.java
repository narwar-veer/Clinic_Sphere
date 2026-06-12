package com.clinic.service;

import com.clinic.entity.OutboxEvent;
import com.clinic.entity.OutboxStatus;
import com.clinic.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxDispatcherService {

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AppTime appTime;
    private final AuditLogService auditLogService;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.max-retries:10}")
    private int maxRetries;

    @Value("${app.outbox.base-retry-seconds:30}")
    private long baseRetrySeconds;

    @Value("${app.outbox.lock-minutes:5}")
    private long lockMinutes;

    @Scheduled(cron = "${app.outbox.poll-cron:*/20 * * * * *}")
    @Transactional
    public void pollAndDispatch() {
        LocalDateTime now = appTime.nowDateTime();
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(now, now.minusMinutes(lockMinutes), batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxEvent event) {
        LocalDateTime now = appTime.nowDateTime();
        event.setStatus(OutboxStatus.PROCESSING);
        event.setLockedAt(now);

        try {
            if ("APPOINTMENT_CONFIRMATION".equals(event.getEventType())) {
                AppointmentNotificationOutboxPayload payload = objectMapper.readValue(
                        event.getPayloadJson(), AppointmentNotificationOutboxPayload.class);
                notificationService.deliverAppointmentConfirmation(payload.appointmentId(), event.getEventKey());
            }
            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(now);
            event.setLastError(null);
            event.setLockedAt(null);
            outboxEventRepository.save(event);
        } catch (Exception ex) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setLastError(ex.getMessage());
            event.setLockedAt(null);
            if (retries >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                event.setProcessedAt(now);
            } else {
                event.setStatus(OutboxStatus.RETRY);
                event.setNextAttemptAt(now.plusSeconds((long) Math.pow(2, retries - 1) * baseRetrySeconds));
            }
            outboxEventRepository.save(event);
            auditLogService.logEvent(
                    "OUTBOX_DELIVERY_FAILED",
                    "system",
                    event.getAggregateType(),
                    event.getAggregateId(),
                    java.util.Map.of(
                            "eventKey", event.getEventKey(),
                            "eventType", event.getEventType(),
                            "retryCount", retries,
                            "error", ex.getMessage() == null ? "unknown" : ex.getMessage()));
            log.error("Outbox dispatch failed id={} retryCount={}", event.getId(), retries, ex);
        }
    }
}