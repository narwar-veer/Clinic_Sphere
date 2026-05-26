package com.clinic.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResponse {
    private String token;
    private String username;
    private String role;
    private Long doctorId;
}
