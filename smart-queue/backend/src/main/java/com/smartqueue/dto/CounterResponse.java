package com.smartqueue.dto;

public record CounterResponse(
        String id,
        int counterNumber,
        String serviceId,
        boolean active) {
}
