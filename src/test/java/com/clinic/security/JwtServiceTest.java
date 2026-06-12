package com.clinic.security;

import com.clinic.entity.Admin;
import com.clinic.entity.AdminRole;
import com.clinic.entity.Doctor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        jwtService = new JwtService(clock, ZoneId.of("Asia/Kolkata"));
        ReflectionTestUtils.setField(jwtService, "secret", "this-is-a-very-strong-32-byte-secret-key-value");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "clinic-app");
        ReflectionTestUtils.setField(jwtService, "audience", "clinic-api");
        ReflectionTestUtils.setField(jwtService, "clockSkewSeconds", 30L);
        ReflectionTestUtils.invokeMethod(jwtService, "validateJwtConfig");
    }

    @Test
    void generateAndParseToken_withRequiredClaims() {
        AdminPrincipal principal = createPrincipal();

        String token = jwtService.generateToken(principal);
        JwtPrincipalClaims claims = jwtService.parsePrincipalClaims(token);

        Assertions.assertEquals(principal.getUsername(), claims.username());
        Assertions.assertEquals(principal.getDoctorId(), claims.doctorId());
        Assertions.assertEquals(principal.getAdminId(), claims.adminId());
        Assertions.assertEquals("ROLE_ADMIN", claims.role());
        Assertions.assertNotNull(claims.tokenId());
    }

    @Test
    void parsePrincipalClaims_invalidSignature_throws() {
        AdminPrincipal principal = createPrincipal();
        String token = jwtService.generateToken(principal);
        String tampered = token.substring(0, token.length() - 1) + "x";

        Assertions.assertThrows(IllegalArgumentException.class, () -> jwtService.parsePrincipalClaims(tampered));
    }

    private AdminPrincipal createPrincipal() {
        Doctor doctor = new Doctor();
        doctor.setId(99L);

        Admin admin = new Admin();
        admin.setId(7L);
        admin.setDoctor(doctor);
        admin.setUsername("root");
        admin.setPasswordHash("hash");
        admin.setRole(AdminRole.ROLE_ADMIN);

        return new AdminPrincipal(admin);
    }
}