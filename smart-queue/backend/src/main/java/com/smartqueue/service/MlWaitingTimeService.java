package com.smartqueue.service;

import com.smartqueue.dto.MlWaitingTimeRequest;
import com.smartqueue.dto.MlWaitingTimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.OptionalDouble;

@Service
public class MlWaitingTimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MlWaitingTimeService.class);

    private final MlPredictionClient predictionClient;
    private final boolean enabled;

    public MlWaitingTimeService(
            MlPredictionClient predictionClient,
            @Value("${ml.service.enabled:true}") boolean enabled) {
        this.predictionClient = predictionClient;
        this.enabled = enabled;
    }

    public OptionalDouble predict(MlWaitingTimeRequest request, String tokenNumber) {
        if (!enabled) {
            return OptionalDouble.empty();
        }

        if (!validRequest(request)) {
            LOGGER.warn("Invalid ML input for token {}. Using fallback waiting-time calculation.", tokenNumber);
            return OptionalDouble.empty();
        }

        try {
            MlWaitingTimeResponse response = predictionClient.predict(request);
            if (response == null
                    || response.predictedWaitingTime() == null
                    || !Double.isFinite(response.predictedWaitingTime())
                    || response.predictedWaitingTime() < 0) {
                LOGGER.warn("Invalid ML prediction for token {}. Using fallback waiting-time calculation.",
                        tokenNumber);
                return OptionalDouble.empty();
            }

            double prediction = roundPrediction(response.predictedWaitingTime());
            LOGGER.info("ML prediction successful for token {}", tokenNumber);
            return OptionalDouble.of(prediction);
        } catch (RuntimeException exception) {
            LOGGER.warn("ML service unavailable. Using fallback waiting-time calculation.", exception);
            return OptionalDouble.empty();
        }
    }

    private boolean validRequest(MlWaitingTimeRequest request) {
        return request != null
                && request.queueSize() >= 0
                && request.tokensAhead() >= 0
                && request.activeCounters() > 0
                && request.averageServiceTime() > 0
                && Double.isFinite(request.averageServiceTime())
                && request.hourOfDay() >= 0
                && request.hourOfDay() <= 23;
    }

    private double roundPrediction(double prediction) {
        return Math.round(Math.max(0.0, prediction) * 100.0) / 100.0;
    }
}
