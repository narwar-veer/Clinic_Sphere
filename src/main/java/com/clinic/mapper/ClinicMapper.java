package com.clinic.mapper;

import com.clinic.dto.response.ClinicResponse;
import com.clinic.entity.Clinic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClinicMapper {

    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", source = "doctor.name")
    ClinicResponse toResponse(Clinic clinic);
}
