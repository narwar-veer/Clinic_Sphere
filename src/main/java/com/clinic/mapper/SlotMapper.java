package com.clinic.mapper;

import com.clinic.dto.response.SlotResponse;
import com.clinic.entity.Slot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SlotMapper {

    @Mapping(target = "clinicId", source = "clinic.id")
    @Mapping(target = "clinicName", source = "clinic.clinicName")
    @Mapping(target = "doctorId", source = "clinic.doctor.id")
    @Mapping(target = "doctorName", source = "clinic.doctor.name")
    @Mapping(target = "blocked", source = "isBlocked")
    @Mapping(target = "availableCapacity", expression = "java(slot.getMaxPatients() - slot.getBookedCount())")
    SlotResponse toResponse(Slot slot);
}
