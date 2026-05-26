package com.clinic.service;

import com.clinic.dto.response.ClinicResponse;
import com.clinic.dto.response.PageResponse;
import com.clinic.mapper.ClinicMapper;
import com.clinic.repository.ClinicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClinicService {

    private final ClinicRepository clinicRepository;
    private final ClinicMapper clinicMapper;
    private final PageResponseFactory pageResponseFactory;

    @Transactional(readOnly = true)
    public PageResponse<ClinicResponse> getClinics(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("clinicName").ascending());
        Page<ClinicResponse> mapped = clinicRepository.findAll(pageable).map(clinicMapper::toResponse);
        return pageResponseFactory.fromPage(mapped);
    }
}
