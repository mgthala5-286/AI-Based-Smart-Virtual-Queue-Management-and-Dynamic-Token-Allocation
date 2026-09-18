package com.smartqueue.dto;

public record QueueSizeResponse(
        String serviceId,
        int queueSize) {
}
