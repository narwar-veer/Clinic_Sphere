package com.clinic.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {
    private static final String DEFAULT_JWT_SECRET = "change-this-secret-key-with-at-least-32-characters";
    private static final long DEFAULT_EXPIRATION_MS = 86_400_000L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${app.jwt.secret:change-this-secret-key-with-at-least-32-characters}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private String expirationMsValue;

    @PostConstruct
    void logStartup() {
        log.info("JwtService initialized in safe mode");
    }

    public String generateToken(AdminPrincipal principal) {
        long nowMs = System.currentTimeMillis();
        long expMs = nowMs + resolveExpirationMs();

        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", principal.getUsername());
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", nowMs / 1000);
        payload.put("exp", expMs / 1000);
        payload.put("doctorId", principal.getDoctorId());
        payload.put("role", principal.getAuthorities().iterator().next().getAuthority());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    public String extractUsername(String token) {
        return asString(parseAndValidatePayload(token).get("sub"));
    }

    public String extractTokenId(String token) {
        return asString(parseAndValidatePayload(token).get("jti"));
    }

    public LocalDateTime extractExpiration(String token) {
        long expEpochSec = asLong(parseAndValidatePayload(token).get("exp"));
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(expEpochSec), ZoneId.systemDefault());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Map<String, Object> payload = parseAndValidatePayload(token);
            String username = asString(payload.get("sub"));
            long exp = asLong(payload.get("exp"));
            return username.equals(userDetails.getUsername()) && exp > Instant.now().getEpochSecond();
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> parseAndValidatePayload(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token is blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT token format is invalid");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("JWT signature is invalid");
        }

        try {
            return OBJECT_MAPPER.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT payload is invalid", ex);
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize JWT section", ex);
        }
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecretBytes(), HMAC_ALGORITHM));
            return URL_ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private byte[] resolveSecretBytes() {
        String value = secret;
        if (value == null || value.trim().isEmpty()) {
            value = DEFAULT_JWT_SECRET;
            log.warn("JWT secret is blank. Falling back to default secret.");
        } else {
            value = value.trim();
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private long resolveExpirationMs() {
        String raw = expirationMsValue;
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_EXPIRATION_MS;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid JWT expiration '{}'. Falling back to {}.", raw, DEFAULT_EXPIRATION_MS);
            return DEFAULT_EXPIRATION_MS;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing JWT claim");
        }
        return String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        throw new IllegalArgumentException("Invalid JWT numeric claim");
    }
}
