package com.clinic.service;

import com.clinic.dto.request.DoctorProfileUpdateRequest;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.entity.Doctor;
import com.clinic.exception.BadRequestException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;
    private final PageResponseFactory pageResponseFactory;

    public PageResponse<DoctorResponse> getDoctors(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<DoctorResponse> mapped = doctorRepository.findAll(pageable).map(doctorMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorProfile(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        return doctorMapper.toResponse(doctor);
    }

    @Transactional
    public DoctorResponse updateDoctorProfile(Long doctorId, DoctorProfileUpdateRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));

        String registrationNumber = request.getRegistrationNumber().trim();
        if (doctorRepository.existsByRegistrationNumberAndIdNot(registrationNumber, doctorId)) {
            throw new BadRequestException("Registration number already exists");
        }

        doctor.setName(request.getName().trim());
        doctor.setPhoto(request.getPhoto());
        doctor.setDegrees(request.getQualifications().trim());
        doctor.setSpecialization(request.getSpecialization().trim());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setRegistrationNumber(registrationNumber);
        doctor.setBio(request.getBiography());
        doctor.setAwards(request.getAwards());

        doctor = doctorRepository.save(doctor);
        return doctorMapper.toResponse(doctor);
    }
}
