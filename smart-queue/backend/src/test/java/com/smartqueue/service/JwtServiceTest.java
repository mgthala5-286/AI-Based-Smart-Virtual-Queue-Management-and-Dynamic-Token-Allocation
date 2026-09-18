package com.smartqueue.service;

import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-jwt-secret-key-that-is-long-enough-123456",
            3_600_000);

    @Test
    void generatedJwtContainsExpectedClaimsAndIsValid() {
        User user = User.builder()
                .id("user-1")
                .name("John")
                .email("john@test.com")
                .role(UserRole.USER)
                .build();

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.validateToken(token));
        assertEquals("user-1", jwtService.extractUserId(token));
        assertEquals("john@test.com", jwtService.extractEmail(token));
        assertEquals("USER", jwtService.extractRole(token));
    }

    @Test
    void invalidJwtIsRejected() {
        assertFalse(jwtService.validateToken("not-a-valid-jwt"));
    }
}
