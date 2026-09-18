package com.smartqueue.dto;

import com.smartqueue.model.TokenStatus;

import java.time.LocalDateTime;

public record TokenResponse(
        String id,
        String tokenNumber,
        String serviceId,
        TokenStatus status,
        LocalDateTime createdAt,
        int queuePosition,
        double estimatedWaitTime) {
}
