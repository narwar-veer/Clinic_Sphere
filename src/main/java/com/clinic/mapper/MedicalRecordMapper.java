package com.clinic.mapper;

import com.clinic.dto.response.MedicalRecordResponse;
import com.clinic.entity.MedicalRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicalRecordMapper {

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", source = "doctor.name")
    @Mapping(target = "appointmentId", source = "appointment.id")
    MedicalRecordResponse toResponse(MedicalRecord medicalRecord);
}
