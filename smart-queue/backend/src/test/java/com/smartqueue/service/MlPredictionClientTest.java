package com.smartqueue.service;

import com.smartqueue.dto.MlWaitingTimeRequest;
import com.smartqueue.dto.MlWaitingTimeResponse;
import com.smartqueue.exception.MlPredictionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MlPredictionClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MlPredictionClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new MlPredictionClient(restTemplate, "http://localhost:8000/");
    }

    @Test
    void sendsExpectedPayloadAndParsesPrediction() {
        server.expect(requestTo("http://localhost:8000/predict/waiting-time"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "queueSize": 10,
                          "tokensAhead": 4,
                          "activeCounters": 2,
                          "averageServiceTime": 10.0,
                          "hourOfDay": 14
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"predictedWaitingTime\":18.5}",
                        MediaType.APPLICATION_JSON));

        MlWaitingTimeResponse response = client.predict(new MlWaitingTimeRequest(
                10, 4, 2, 10, 14));

        assertEquals(18.5, response.predictedWaitingTime());
        server.verify();
    }

    @Test
    void convertsHttpErrorsToMlPredictionException() {
        server.expect(requestTo("http://localhost:8000/predict/waiting-time"))
                .andRespond(withServerError());

        assertThrows(MlPredictionException.class, () -> client.predict(
                new MlWaitingTimeRequest(10, 4, 2, 10, 14)));
    }
}
