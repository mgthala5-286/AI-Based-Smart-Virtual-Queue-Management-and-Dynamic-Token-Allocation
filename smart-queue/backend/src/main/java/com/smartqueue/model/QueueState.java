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
public class QueueState {

    private String serviceId;
    private int queueSize;
    private int activeCounters;
    private LocalDateTime timestamp;
}
