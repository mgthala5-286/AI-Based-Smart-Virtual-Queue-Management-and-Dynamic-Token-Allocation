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
public class Prediction {

    private String id;
    private String tokenId;
    private int queueSize;
    private int tokensAhead;
    private int activeCounters;
    private double averageServiceTime;
    private int hourOfDay;
    private double predictedWaitingTime;
    private LocalDateTime predictionTime;
    private String source;
}
