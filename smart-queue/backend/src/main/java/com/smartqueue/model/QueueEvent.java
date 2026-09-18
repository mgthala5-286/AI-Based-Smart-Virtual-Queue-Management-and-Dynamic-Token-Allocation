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
public class QueueEvent {

    private String id;
    private String tokenId;
    private String eventType;
    private int oldPosition;
    private int newPosition;
    private LocalDateTime timestamp;
}
