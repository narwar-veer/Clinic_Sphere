package com.clinic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    private static final String TOKEN_TYPE = "access";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_DOCTOR_ID = "doctorId";
    private static final String CLAIM_ADMIN_ID = "adminId";

    private final Clock appClock;
    private final ZoneId appZoneId;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${app.jwt.issuer:clinic-app}")
    private String issuer;

    @Value("${app.jwt.audience:clinic-api}")
    private String audience;

    @Value("${app.jwt.clock-skew-seconds:30}")
    private long clockSkewSeconds;

    private SecretKey signingKey;

    public JwtService(Clock appClock, ZoneId appZoneId) {
        this.appClock = appClock;
        this.appZoneId = appZoneId;
    }

    @PostConstruct
    void validateJwtConfig() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is required");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException ignored) {
            keyBytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }

        signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService initialized issuer={} audience={} expirationMs={}", issuer, audience, expirationMs);
    }

    public String generateToken(AdminPrincipal principal) {
        Instant now = Instant.now(appClock);
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now.minusSeconds(1)))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TOKEN_TYPE)
                .claim(CLAIM_ADMIN_ID, principal.getAdminId())
                .claim(CLAIM_DOCTOR_ID, principal.getDoctorId())
                .claim(CLAIM_ROLE, principal.getAuthorities().iterator().next().getAuthority())
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipalClaims parsePrincipalClaims(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!TOKEN_TYPE.equals(type)) {
            throw new IllegalArgumentException("Invalid token type");
        }
        Long adminId = claims.get(CLAIM_ADMIN_ID, Long.class);
        Long doctorId = claims.get(CLAIM_DOCTOR_ID, Long.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        String username = claims.getSubject();
        String tokenId = claims.getId();
        Date expiration = claims.getExpiration();

        if (adminId == null || doctorId == null || role == null || username == null || tokenId == null || expiration == null) {
            throw new IllegalArgumentException("Missing required token claims");
        }

        return new JwtPrincipalClaims(adminId, doctorId, username, role, tokenId, expiration.toInstant());
    }

    public String extractTokenId(String token) {
        return parseClaims(token).getId();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public LocalDateTime extractExpiration(String token) {
        return LocalDateTime.ofInstant(parseClaims(token).getExpiration().toInstant(), appZoneId);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();
            return username != null
                    && username.equals(userDetails.getUsername())
                    && claims.getExpiration() != null
                    && claims.getExpiration().toInstant().isAfter(Instant.now(appClock));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .clock(() -> Date.from(Instant.now(appClock)))
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid JWT", ex);
        }
    }
}
