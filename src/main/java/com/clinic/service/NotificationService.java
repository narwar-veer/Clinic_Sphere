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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final RestClient.Builder restClientBuilder;

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

    @Async("notificationExecutor")
    public void sendAppointmentConfirmationAsync(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found for notification"));
        String message = buildAppointmentMessage(appointment);
        sendSmsNotification(appointment, message);
        sendWhatsAppNotification(appointment, message);
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

    private void sendSmsNotification(Appointment appointment, String message) {
        Notification notification = createNotification(appointment, NotificationType.SMS);
        try {
            if (!twilioEnabled) {
                markMockSent(notification, "twilio-mock");
                return;
            }
            RestClient client = restClientBuilder.baseUrl("https://api.twilio.com").build();
            client.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilioAccountSid)
                    .headers(headers -> headers.setBasicAuth(twilioAccountSid, twilioAuthToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("To=" + encodeFormValue(appointment.getPatient().getPhone())
                            + "&From=" + encodeFormValue(twilioFromPhone)
                            + "&Body=" + encodeFormValue(message))
                    .retrieve()
                    .toBodilessEntity();
            markSent(notification, "twilio-live");
        } catch (Exception ex) {
            markFailed(notification, ex);
        }
    }

    private void sendWhatsAppNotification(Appointment appointment, String message) {
        Notification notification = createNotification(appointment, NotificationType.WA);
        try {
            if (!whatsappEnabled) {
                markMockSent(notification, "whatsapp-mock");
                return;
            }
            RestClient client = restClientBuilder.baseUrl(whatsappApiUrl).build();
            String payload = """
                    {
                      "from": "%s",
                      "to": "%s",
                      "type": "text",
                      "text": { "body": "%s" }
                    }
                    """.formatted(whatsappFromNumber, appointment.getPatient().getPhone(), escapeJson(message));

            client.post()
                    .uri("")
                    .headers(headers -> headers.setBearerAuth(whatsappToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            markSent(notification, "wa-live");
        } catch (Exception ex) {
            markFailed(notification, ex);
        }
    }

    private Notification createNotification(Appointment appointment, NotificationType type) {
        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setType(type);
        notification.setStatus(NotificationStatus.PENDING);
        return notificationRepository.save(notification);
    }

    private void markMockSent(Notification notification, String providerId) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setProviderMessageId(providerId);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private void markSent(Notification notification, String providerId) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setProviderMessageId(providerId);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Notification sent notificationId={} type={}", notification.getId(), notification.getType());
    }

    private void markFailed(Notification notification, Exception ex) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setErrorMessage(ex.getMessage());
        notificationRepository.save(notification);
        log.error("Failed to send notification notificationId={} type={}", notification.getId(), notification.getType(), ex);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String encodeFormValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
