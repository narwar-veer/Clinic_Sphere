package com.clinic.service;

import com.clinic.security.JwtService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;

class AdminServiceTest {

    @Test
    void logout_isIdempotentForMissingOrInvalidToken() {
        AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        AdminSessionService adminSessionService = Mockito.mock(AdminSessionService.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);

        AdminService adminService = new AdminService(
                authenticationManager,
                jwtService,
                adminSessionService,
                auditLogService,
                new SimpleMeterRegistry());

        adminService.logout(null);
        adminService.logout("Bearer invalid-token");

        Mockito.verify(adminSessionService, Mockito.never()).revokeSession(Mockito.anyString());
    }
}