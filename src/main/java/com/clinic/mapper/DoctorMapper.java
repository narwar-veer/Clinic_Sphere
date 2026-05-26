package com.clinic.mapper;

import com.clinic.dto.response.DoctorResponse;
import com.clinic.entity.Doctor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    @Mapping(target = "qualifications", source = "degrees")
    DoctorResponse toResponse(Doctor doctor);
}
