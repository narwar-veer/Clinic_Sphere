package com.clinic.security;

import java.time.Instant;

public record JwtPrincipalClaims(
        Long adminId,
        Long doctorId,
        String username,
        String role,
        String tokenId,
        Instant expiresAt
) {
}