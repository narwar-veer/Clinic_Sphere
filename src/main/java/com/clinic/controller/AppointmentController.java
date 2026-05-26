package com.clinic.controller;

import com.clinic.dto.request.AppointmentBookingRequest;
import com.clinic.dto.request.AppointmentTestimonialRequest;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse bookAppointment(@Valid @RequestBody AppointmentBookingRequest request) {
        return appointmentService.bookAppointment(request);
    }

    @PostMapping("/{id}/testimonial")
    public AppointmentResponse submitTestimonial(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentTestimonialRequest request
    ) {
        return appointmentService.submitTestimonial(id, request);
    }
}
