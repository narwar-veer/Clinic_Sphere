package com.clinic.service;

import com.clinic.dto.request.AdminLoginRequest;
import com.clinic.dto.response.AdminLoginResponse;
import com.clinic.exception.UnauthorizedException;
import com.clinic.security.AdminPrincipal;
import com.clinic.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdminSessionService adminSessionService;

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
            return AdminLoginResponse.builder()
                    .token(token)
                    .username(principal.getUsername())
                    .role(principal.getAuthorities().iterator().next().getAuthority())
                    .doctorId(principal.getDoctorId())
                    .build();
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid username or password");
        }
    }

    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new UnauthorizedException("Authentication token is required");
        }
        try {
            String tokenId = jwtService.extractTokenId(token);
            if (tokenId != null && !tokenId.isBlank()) {
                adminSessionService.revokeSession(tokenId);
            }
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid authentication token");
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
