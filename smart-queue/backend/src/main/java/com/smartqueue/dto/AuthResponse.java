package com.smartqueue.dto;

import com.smartqueue.model.UserRole;

public record AuthResponse(
        String token,
        String userId,
        String name,
        UserRole role) {
}
