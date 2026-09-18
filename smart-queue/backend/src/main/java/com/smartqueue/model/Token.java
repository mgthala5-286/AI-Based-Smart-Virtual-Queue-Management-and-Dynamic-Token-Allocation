package com.smartqueue.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    private String id;
    private String tokenNumber;
    private String userId;
    private String serviceId;
    private String counterId;
    private TokenStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime calledAt;
    private LocalDateTime completedAt;
    private int queuePosition;
    private double estimatedWaitTime;
}
