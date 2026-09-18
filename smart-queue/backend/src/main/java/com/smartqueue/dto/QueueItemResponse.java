package com.smartqueue.dto;

import com.smartqueue.model.TokenStatus;

public record QueueItemResponse(
        String tokenNumber,
        int position,
        TokenStatus status,
        double estimatedWaitTime) {
}
