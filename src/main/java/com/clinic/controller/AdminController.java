package com.clinic.controller;

import com.clinic.dto.request.AdminAppointmentUpdateRequest;
import com.clinic.dto.request.AdminLoginRequest;
import com.clinic.dto.request.DoctorProfileUpdateRequest;
import com.clinic.dto.request.SlotCapacityUpdateRequest;
import com.clinic.dto.request.SlotConfigUpdateRequest;
import com.clinic.dto.response.AdminLoginResponse;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.MessageResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.dto.response.SlotConfigResponse;
import com.clinic.dto.response.SlotResponse;
import com.clinic.entity.AppointmentStatus;
import com.clinic.service.AdminService;
import com.clinic.service.AppointmentExportService;
import com.clinic.service.AppointmentService;
import com.clinic.service.DoctorService;
import com.clinic.service.SlotService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AppointmentService appointmentService;
    private final AppointmentExportService appointmentExportService;
    private final DoctorService doctorService;
    private final SlotService slotService;

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return adminService.login(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        adminService.logout(authorizationHeader);
        return new MessageResponse("Logged out successfully");
    }

    @GetMapping("/appointments")
    public PageResponse<AppointmentResponse> getAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Boolean visited
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return appointmentService.getAdminAppointments(doctorId, page, size, date, time, name, phone, status, visited);
    }

    @PutMapping("/appointments/{id}")
    public AppointmentResponse updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AdminAppointmentUpdateRequest request
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return appointmentService.updateAppointmentStatus(doctorId, id, request);
    }

    @GetMapping("/appointments/export")
    public ResponseEntity<ByteArrayResource> exportAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Boolean visited
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        byte[] file = appointmentExportService.exportDoctorAppointments(doctorId, date, time, name, phone, status, visited);

        String filename = "appointments-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(file.length)
                .body(new ByteArrayResource(file));
    }

    @GetMapping("/profile")
    public DoctorResponse getDoctorProfile() {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return doctorService.getDoctorProfile(doctorId);
    }

    @PutMapping("/profile")
    public DoctorResponse updateDoctorProfile(@Valid @RequestBody DoctorProfileUpdateRequest request) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return doctorService.updateDoctorProfile(doctorId, request);
    }

    @GetMapping("/slot-config")
    public SlotConfigResponse getSlotConfig(@RequestParam Long clinicId) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return slotService.getSlotConfig(doctorId, clinicId);
    }

    @PutMapping("/slot-config")
    public SlotConfigResponse updateSlotConfig(
            @RequestParam Long clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @Valid @RequestBody SlotConfigUpdateRequest request
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return slotService.updateSlotConfig(doctorId, clinicId, effectiveDate, request);
    }

    @PutMapping("/slot-capacity")
    public MessageResponse updateSlotCapacity(
            @RequestParam Long clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @Valid @RequestBody SlotCapacityUpdateRequest request
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return slotService.updateSlotCapacity(doctorId, clinicId, effectiveDate, request.getMaxPatientsPerSlot());
    }

    @PutMapping("/slots/{id}/block")
    public SlotResponse blockSlot(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean blocked
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return slotService.setSlotBlocked(doctorId, id, blocked);
    }

    @PutMapping("/slots/block-date")
    public MessageResponse blockDate(
            @RequestParam Long clinicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean blocked
    ) {
        Long doctorId = adminService.getAuthenticatedDoctorId();
        return slotService.setDateBlocked(doctorId, clinicId, date, blocked);
    }
}
