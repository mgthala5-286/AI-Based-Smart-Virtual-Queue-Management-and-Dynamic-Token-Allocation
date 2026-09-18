package com.smartqueue.dto;

public record MlWaitingTimeRequest(
        int queueSize,
        int tokensAhead,
        int activeCounters,
        double averageServiceTime,
        int hourOfDay) {
}
