package com.clinic.service;

import com.clinic.dto.response.PageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PageResponseFactory {

    public <T> PageResponse<T> fromPage(Page<T> page) {
        List<T> content = page.getContent();
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
