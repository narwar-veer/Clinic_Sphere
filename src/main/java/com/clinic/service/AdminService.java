package com.clinic.service;

import com.clinic.dto.request.AdminLoginRequest;
import com.clinic.dto.response.AdminLoginResponse;
import com.clinic.exception.UnauthorizedException;
import com.clinic.security.AdminPrincipal;
import com.clinic.security.JwtService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdminSessionService adminSessionService;
    private final AuditLogService auditLogService;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;

    public AdminService(AuthenticationManager authenticationManager,
                        JwtService jwtService,
                        AdminSessionService adminSessionService,
                        AuditLogService auditLogService,
                        MeterRegistry meterRegistry) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.adminSessionService = adminSessionService;
        this.auditLogService = auditLogService;
        this.loginSuccessCounter = Counter.builder("clinic.auth.login.success").register(meterRegistry);
        this.loginFailureCounter = Counter.builder("clinic.auth.login.failure").register(meterRegistry);
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();
            String token = jwtService.generateToken(principal);
            String tokenId = jwtService.extractTokenId(token);
            if (tokenId == null || tokenId.isBlank()) {
                throw new UnauthorizedException("Failed to create authenticated session");
            }

            adminSessionService.registerSession(
                    tokenId,
                    principal.getUsername(),
                    principal.getDoctorId(),
                    jwtService.extractExpiration(token)
            );

            loginSuccessCounter.increment();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("doctorId", principal.getDoctorId());
            details.put("tokenId", tokenId);
            auditLogService.logEvent(
                    "ADMIN_LOGIN_SUCCESS",
                    principal.getUsername(),
                    "ADMIN",
                    principal.getAdminId().toString(),
                    details);

            return AdminLoginResponse.builder()
                    .token(token)
                    .username(principal.getUsername())
                    .role(principal.getAuthorities().iterator().next().getAuthority())
                    .doctorId(principal.getDoctorId())
                    .build();
        } catch (BadCredentialsException ex) {
            loginFailureCounter.increment();
            auditLogService.logEvent(
                    "ADMIN_LOGIN_FAILURE",
                    request.getUsername(),
                    "ADMIN",
                    "unknown",
                    Map.of("reason", "bad_credentials"));
            throw new UnauthorizedException("Invalid username or password");
        }
    }

    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token != null) {
            try {
                String tokenId = jwtService.extractTokenId(token);
                if (tokenId != null && !tokenId.isBlank()) {
                    adminSessionService.revokeSession(tokenId);
                    auditLogService.logEvent(
                            "ADMIN_LOGOUT",
                            getAuthenticatedUsernameOrUnknown(),
                            "SESSION",
                            tokenId,
                            Map.of());
                }
            } catch (IllegalArgumentException ignored) {
                // idempotent logout: always return success
            }
        }
        SecurityContextHolder.clearContext();
    }

    public Long getAuthenticatedDoctorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminPrincipal adminPrincipal && adminPrincipal.getDoctorId() != null) {
            return adminPrincipal.getDoctorId();
        }
        throw new UnauthorizedException("Unable to resolve authenticated admin");
    }

    private String getAuthenticatedUsernameOrUnknown() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            return principal.getUsername();
        }
        return "unknown";
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
