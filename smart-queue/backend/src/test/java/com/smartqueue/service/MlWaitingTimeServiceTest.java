package com.smartqueue.service;

import com.smartqueue.dto.MlWaitingTimeRequest;
import com.smartqueue.dto.MlWaitingTimeResponse;
import com.smartqueue.exception.MlPredictionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlWaitingTimeServiceTest {

    @Mock
    private MlPredictionClient predictionClient;

    private MlWaitingTimeService service;

    @BeforeEach
    void setUp() {
        service = new MlWaitingTimeService(predictionClient, true);
    }

    @Test
    void returnsValidRoundedPrediction() {
        when(predictionClient.predict(validRequest()))
                .thenReturn(new MlWaitingTimeResponse(18.567));

        OptionalDouble result = service.predict(validRequest(), "G003");

        assertTrue(result.isPresent());
        assertEquals(18.57, result.getAsDouble());
    }

    @Test
    void rejectsNegativePrediction() {
        when(predictionClient.predict(validRequest()))
                .thenReturn(new MlWaitingTimeResponse(-1.0));

        assertFalse(service.predict(validRequest(), "G003").isPresent());
    }

    @Test
    void rejectsNullPrediction() {
        when(predictionClient.predict(validRequest()))
                .thenReturn(new MlWaitingTimeResponse(null));

        assertFalse(service.predict(validRequest(), "G003").isPresent());
    }

    @Test
    void usesEmptyResultWhenMlServiceFails() {
        when(predictionClient.predict(validRequest()))
                .thenThrow(new MlPredictionException("offline"));

        assertFalse(service.predict(validRequest(), "G003").isPresent());
    }

    @Test
    void doesNotCallMlForInvalidInput() {
        assertFalse(service.predict(new MlWaitingTimeRequest(
                10, 4, 0, 10, 14), "G003").isPresent());
        verifyNoInteractions(predictionClient);
    }

    private MlWaitingTimeRequest validRequest() {
        return new MlWaitingTimeRequest(10, 4, 2, 10, 14);
    }
}
