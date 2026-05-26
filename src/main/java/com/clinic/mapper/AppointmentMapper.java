package com.clinic.mapper;

import com.clinic.dto.response.AppointmentResponse;
import com.clinic.entity.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientName", source = "patient.name")
    @Mapping(target = "patientPhone", source = "patient.phone")
    @Mapping(target = "patientAge", source = "patient.age")
    @Mapping(target = "patientGender", source = "patient.gender")
    @Mapping(target = "slotId", source = "slot.id")
    @Mapping(target = "clinicId", source = "slot.clinic.id")
    @Mapping(target = "clinicName", source = "slot.clinic.clinicName")
    @Mapping(target = "doctorName", source = "slot.clinic.doctor.name")
    @Mapping(target = "slotDate", source = "slot.slotDate")
    @Mapping(target = "slotStartTime", source = "slot.startTime")
    @Mapping(target = "slotEndTime", source = "slot.endTime")
    AppointmentResponse toResponse(Appointment appointment);
}
