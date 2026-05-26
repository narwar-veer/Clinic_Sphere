package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public byte[] exportDoctorAppointments(
            Long doctorId,
            LocalDate date,
            LocalTime time,
            String name,
            String phone,
            AppointmentStatus status,
            Boolean visited
    ) {
        var specification = AppointmentSpecifications.forDoctorAppointments(doctorId, date, time, name, phone, status, visited);
        List<Appointment> appointments = appointmentRepository.findAll(specification, Sort.by("createdAt").descending());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Appointments");
            createHeader(sheet);

            int rowIndex = 1;
            for (Appointment appointment : appointments) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(appointment.getPatient().getName());
                row.createCell(1).setCellValue(appointment.getPatient().getPhone());
                row.createCell(2).setCellValue(appointment.getPatient().getAge());
                row.createCell(3).setCellValue(appointment.getSlot().getSlotDate().format(DATE_FORMAT));
                row.createCell(4).setCellValue(appointment.getSlot().getStartTime().format(TIME_FORMAT));
                row.createCell(5).setCellValue(appointment.getStatus().name());
                row.createCell(6).setCellValue(
                        appointment.getVisitedAt() == null ? "" : appointment.getVisitedAt().format(DATETIME_FORMAT));
                row.createCell(7).setCellValue(appointment.getRating() == null ? "" : appointment.getRating().toString());
                row.createCell(8).setCellValue(nullSafe(appointment.getTestimonial()));
                row.createCell(9).setCellValue(nullSafe(appointment.getSymptoms()));
                row.createCell(10).setCellValue(appointment.getCreatedAt().format(DATETIME_FORMAT));
            }

            for (int i = 0; i < 11; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export appointments", ex);
        }
    }

    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Patient");
        header.createCell(1).setCellValue("Phone");
        header.createCell(2).setCellValue("Age");
        header.createCell(3).setCellValue("Date");
        header.createCell(4).setCellValue("Slot");
        header.createCell(5).setCellValue("Status");
        header.createCell(6).setCellValue("Visited At");
        header.createCell(7).setCellValue("Rating");
        header.createCell(8).setCellValue("Testimonial");
        header.createCell(9).setCellValue("Symptoms");
        header.createCell(10).setCellValue("Booked At");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
