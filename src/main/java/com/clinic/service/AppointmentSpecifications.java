package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.jpa.domain.Specification;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> forDoctorAppointments(
            Long doctorId,
            LocalDate date,
            LocalTime time,
            String name,
            String phone,
            AppointmentStatus status,
            Boolean visited
    ) {
        Specification<Appointment> specification =
                (root, query, cb) -> cb.equal(root.get("slot").get("clinic").get("doctor").get("id"), doctorId);

        if (date != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("slot").get("slotDate"), date));
        }
        if (time != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("slot").get("startTime"), time));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (name != null && !name.isBlank()) {
            String normalized = "%" + name.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("patient").get("name")), normalized));
        }
        if (phone != null && !phone.isBlank()) {
            String normalized = "%" + phone.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("patient").get("phone")), normalized));
        }
        if (visited != null) {
            if (visited) {
                specification = specification.and((root, query, cb) -> cb.isNotNull(root.get("visitedAt")));
            } else {
                specification = specification.and((root, query, cb) -> cb.isNull(root.get("visitedAt")));
            }
        }
        return specification;
    }
}
