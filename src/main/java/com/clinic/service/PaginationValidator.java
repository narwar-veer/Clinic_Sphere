package com.clinic.service;

import com.clinic.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class PaginationValidator {

    private static final int MAX_PAGE_SIZE = 200;

    public void validate(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}