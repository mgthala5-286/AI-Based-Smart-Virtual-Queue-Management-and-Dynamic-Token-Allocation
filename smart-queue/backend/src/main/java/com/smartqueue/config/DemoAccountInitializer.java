package com.smartqueue.config;

import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "smart-queue.demo-accounts.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DemoAccountInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createIfMissing("admin@smartqueue.com", "admin123", "Smart Queue Admin", UserRole.ADMIN);
        createIfMissing("staff@smartqueue.com", "staff123", "Smart Queue Staff", UserRole.STAFF);
    }

    private void createIfMissing(String email, String password, String name, UserRole role) {
        boolean exists = userRepository.readAll().stream()
                .anyMatch(user -> email.equalsIgnoreCase(user.getEmail()));
        if (!exists) {
            userRepository.save(User.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .build());
        }
    }
}
