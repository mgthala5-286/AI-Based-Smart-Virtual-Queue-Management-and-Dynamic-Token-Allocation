package com.smartqueue.service;

import com.smartqueue.dto.MlWaitingTimeRequest;
import com.smartqueue.dto.MlWaitingTimeResponse;
import com.smartqueue.exception.MlPredictionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class MlPredictionClient {

    private static final String PREDICTION_PATH = "/predict/waiting-time";

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public MlPredictionClient(
            RestTemplate mlRestTemplate,
            @Value("${ml.service.url:http://localhost:8000}") String serviceUrl) {
        this.restTemplate = mlRestTemplate;
        this.serviceUrl = serviceUrl;
    }

    public MlWaitingTimeResponse predict(MlWaitingTimeRequest request) {
        try {
            return restTemplate.postForObject(
                    predictionUrl(),
                    request,
                    MlWaitingTimeResponse.class);
        } catch (RestClientException exception) {
            throw new MlPredictionException(
                    "Could not get a waiting-time prediction from the ML service",
                    exception);
        }
    }

    private String predictionUrl() {
        if (serviceUrl.endsWith("/")) {
            return serviceUrl.substring(0, serviceUrl.length() - 1) + PREDICTION_PATH;
        }
        return serviceUrl + PREDICTION_PATH;
    }
}
