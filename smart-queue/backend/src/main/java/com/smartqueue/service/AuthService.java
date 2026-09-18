package com.smartqueue.service;

import com.smartqueue.dto.AuthResponse;
import com.smartqueue.dto.LoginRequest;
import com.smartqueue.dto.RegisterRequest;
import com.smartqueue.dto.RegistrationResponse;
import com.smartqueue.exception.DuplicateEmailException;
import com.smartqueue.exception.InvalidCredentialsException;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegistrationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        boolean emailExists = userRepository.readAll().stream()
                .anyMatch(user -> email.equalsIgnoreCase(user.getEmail()));
        if (emailExists) {
            throw new DuplicateEmailException("Email is already registered");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .build();

        userRepository.save(user);
        return new RegistrationResponse("Registration successful", user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.readAll().stream()
                .filter(candidate -> email.equalsIgnoreCase(candidate.getEmail()))
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new AuthResponse(
                jwtService.generateToken(user),
                user.getId(),
                user.getName(),
                user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
