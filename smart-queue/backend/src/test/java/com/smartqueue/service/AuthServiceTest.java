package com.smartqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.dto.LoginRequest;
import com.smartqueue.dto.RegisterRequest;
import com.smartqueue.exception.DuplicateEmailException;
import com.smartqueue.exception.InvalidCredentialsException;
import com.smartqueue.model.User;
import com.smartqueue.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    @TempDir
    Path temporaryDirectory;

    private AuthService authService;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        userRepository = new UserRepository(objectMapper, temporaryDirectory.toString());
        passwordEncoder = new BCryptPasswordEncoder();
        JwtService jwtService = new JwtService(
                "test-jwt-secret-key-that-is-long-enough-123456",
                3_600_000);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesUserWithDefaultRole() {
        var response = authService.register(
                new RegisterRequest("John", "john@test.com", "password123"));

        assertEquals("Registration successful", response.message());
        User savedUser = userRepository.findById(response.userId()).orElseThrow();
        assertEquals("John", savedUser.getName());
        assertEquals("USER", savedUser.getRole().name());
    }

    @Test
    void duplicateEmailRegistrationIsRejected() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "password123");
        authService.register(request);

        assertThrows(DuplicateEmailException.class, () -> authService.register(request));
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        authService.register(new RegisterRequest("John", "john@test.com", "password123"));

        User savedUser = userRepository.readAll().get(0);
        assertNotEquals("password123", savedUser.getPassword());
        assertTrue(savedUser.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()));
    }

    @Test
    void loginReturnsJwtAndUserDetails() {
        authService.register(new RegisterRequest("John", "john@test.com", "password123"));

        var response = authService.login(new LoginRequest("john@test.com", "password123"));

        assertNotNull(response.token());
        assertEquals("John", response.name());
        assertEquals("USER", response.role().name());
    }

    @Test
    void invalidPasswordIsRejected() {
        authService.register(new RegisterRequest("John", "john@test.com", "password123"));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("john@test.com", "wrong-password")));
    }
}
