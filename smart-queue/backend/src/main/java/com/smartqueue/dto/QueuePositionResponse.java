package com.smartqueue.dto;

import com.smartqueue.model.TokenStatus;

public record QueuePositionResponse(
        String tokenNumber,
        int position,
        int peopleAhead,
        TokenStatus status,
        double estimatedWaitTime) {
}
