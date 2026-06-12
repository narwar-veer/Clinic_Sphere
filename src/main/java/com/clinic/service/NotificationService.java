package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.Notification;
import com.clinic.entity.NotificationStatus;
import com.clinic.entity.NotificationType;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final RestClient.Builder restClientBuilder;
    private final AppTime appTime;

    @Value("${notification.twilio.enabled:false}")
    private boolean twilioEnabled;

    @Value("${notification.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${notification.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${notification.twilio.from-phone:}")
    private String twilioFromPhone;

    @Value("${notification.whatsapp.enabled:false}")
    private boolean whatsappEnabled;

    @Value("${notification.whatsapp.api-url:}")
    private String whatsappApiUrl;

    @Value("${notification.whatsapp.token:}")
    private String whatsappToken;

    @Value("${notification.whatsapp.from-number:}")
    private String whatsappFromNumber;

    public void deliverAppointmentConfirmation(Long appointmentId, String outboxKey) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found for notification"));

        String message = buildAppointmentMessage(appointment);
        sendSmsNotification(appointment, message, outboxKey + ":SMS");
        sendWhatsAppNotification(appointment, message, outboxKey + ":WA");
    }

    private String buildAppointmentMessage(Appointment appointment) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        return "Appointment Booked. Doctor: "
                + appointment.getSlot().getClinic().getDoctor().getName()
                + ". Date: "
                + appointment.getSlot().getSlotDate().format(dateFormatter)
                + ", "
                + appointment.getSlot().getStartTime().format(timeFormatter)
                + "-"
                + appointment.getSlot().getEndTime().format(timeFormatter)
                + ".";
    }

    private void sendSmsNotification(Appointment appointment, String message, String idempotencyKey) {
        Notification notification = getOrCreateNotification(appointment, NotificationType.SMS, idempotencyKey);
        if (notification.getStatus() == NotificationStatus.SENT) {
            return;
        }

        try {
            if (!twilioEnabled) {
                markSent(notification, "twilio-mock");
                return;
            }

            String providerMessageId = sendTwilioWithRetry(appointment.getPatient().getPhone(), message, idempotencyKey);
            markSent(notification, providerMessageId);
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw ex;
        }
    }

    private void sendWhatsAppNotification(Appointment appointment, String message, String idempotencyKey) {
        Notification notification = getOrCreateNotification(appointment, NotificationType.WA, idempotencyKey);
        if (notification.getStatus() == NotificationStatus.SENT) {
            return;
        }

        try {
            if (!whatsappEnabled) {
                markSent(notification, "whatsapp-mock");
                return;
            }

            String providerMessageId = sendWhatsAppWithRetry(appointment.getPatient().getPhone(), message, idempotencyKey);
            markSent(notification, providerMessageId);
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw ex;
        }
    }

    private String sendTwilioWithRetry(String toPhone, String message, String idempotencyKey) {
        RestClient client = restClientBuilder.baseUrl("https://api.twilio.com").build();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                client.post()
                        .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilioAccountSid)
                        .headers(headers -> {
                            headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
                            headers.add("Idempotency-Key", idempotencyKey);
                        })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("To=" + encodeFormValue(toPhone)
                                + "&From=" + encodeFormValue(twilioFromPhone)
                                + "&Body=" + encodeFormValue(message))
                        .retrieve()
                        .toBodilessEntity();
                return "twilio-live-" + idempotencyKey;
            } catch (RuntimeException ex) {
                last = ex;
                sleepBackoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("Twilio send failed") : last;
    }

    private String sendWhatsAppWithRetry(String toPhone, String message, String idempotencyKey) {
        RestClient client = restClientBuilder.baseUrl(whatsappApiUrl).build();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                client.post()
                        .uri("")
                        .headers(headers -> {
                            headers.setBearerAuth(whatsappToken);
                            headers.add("Idempotency-Key", idempotencyKey);
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "from", whatsappFromNumber,
                                "to", toPhone,
                                "type", "text",
                                "text", Map.of("body", message)
                        ))
                        .retrieve()
                        .toBodilessEntity();
                return "wa-live-" + idempotencyKey;
            } catch (RuntimeException ex) {
                last = ex;
                sleepBackoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("WhatsApp send failed") : last;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt - 1) * 150L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Notification retry interrupted", ex);
        }
    }

    private Notification getOrCreateNotification(Appointment appointment, NotificationType type, String idempotencyKey) {
        return notificationRepository.findByTypeAndIdempotencyKey(type, idempotencyKey)
                .orElseGet(() -> {
                    Notification notification = new Notification();
                    notification.setAppointment(appointment);
                    notification.setType(type);
                    notification.setStatus(NotificationStatus.PENDING);
                    notification.setIdempotencyKey(idempotencyKey);
                    return notificationRepository.save(notification);
                });
    }

    private void markSent(Notification notification, String providerId) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setProviderMessageId(providerId);
        notification.setErrorMessage(null);
        notification.setSentAt(appTime.nowDateTime());
        notificationRepository.save(notification);
        log.info("Notification sent notificationId={} type={}", notification.getId(), notification.getType());
    }

    private void markFailed(Notification notification, Exception ex) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setErrorMessage(ex.getMessage());
        notificationRepository.save(notification);
        log.error("Failed to send notification notificationId={} type={}", notification.getId(), notification.getType(), ex);
    }

    private String encodeFormValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}