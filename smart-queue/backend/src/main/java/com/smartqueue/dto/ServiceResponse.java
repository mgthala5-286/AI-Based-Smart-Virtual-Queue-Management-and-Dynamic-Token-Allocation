package com.smartqueue.dto;

public record ServiceResponse(
        String id,
        String name,
        String description,
        int averageServiceTime,
        boolean active) {
}
