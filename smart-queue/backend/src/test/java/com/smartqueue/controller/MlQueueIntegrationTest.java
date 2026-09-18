package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.dto.MlWaitingTimeResponse;
import com.smartqueue.model.Counter;
import com.smartqueue.model.Prediction;
import com.smartqueue.model.Service;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.PredictionRepository;
import com.smartqueue.repository.QueueEventRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.TokenRepository;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.service.JwtService;
import com.smartqueue.service.MlPredictionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-queue.database-path=target/test-ml-queue-data",
        "smart-queue.demo-accounts.enabled=false",
        "ml.service.enabled=true"
})
class MlQueueIntegrationTest {

    private static final String SERVICE_ID = "service-1";
    private static final String USER_ID = "ml-user";
    private static final String ADMIN_ID = "ml-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CounterRepository counterRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private QueueEventRepository queueEventRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private MlPredictionClient predictionClient;

    @BeforeEach
    void clearAndSeedData() {
        reset(predictionClient);
        predictionRepository.readAll().forEach(prediction -> predictionRepository.delete(prediction.getId()));
        queueEventRepository.readAll().forEach(event -> queueEventRepository.delete(event.getId()));
        tokenRepository.readAll().forEach(token -> tokenRepository.delete(token.getId()));
        counterRepository.readAll().forEach(counter -> counterRepository.delete(counter.getId()));
        serviceRepository.readAll().forEach(service -> serviceRepository.delete(service.getId()));
        userRepository.readAll().forEach(user -> userRepository.delete(user.getId()));

        serviceRepository.save(Service.builder()
                .id(SERVICE_ID)
                .name("General Service")
                .description("ML queue")
                .averageServiceTime(10)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-1")
                .counterNumber(1)
                .serviceId(SERVICE_ID)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-2")
                .counterNumber(2)
                .serviceId(SERVICE_ID)
                .active(true)
                .build());
        userRepository.save(user(USER_ID, UserRole.USER));
        userRepository.save(user(ADMIN_ID, UserRole.ADMIN));
    }

    @Test
    void queueUsesMlPredictionAndStoresHistory() throws Exception {
        when(predictionClient.predict(any()))
                .thenReturn(new MlWaitingTimeResponse(18.567));

        createToken();
        createToken();
        createToken();

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].tokenNumber").value("G001"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitTime").value(18.57))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[2].position").value(3));

        List<Prediction> predictions = predictionRepository.readAll();
        assertTrue(predictions.stream().anyMatch(prediction -> "ML".equals(prediction.getSource())));
        assertTrue(predictions.stream().allMatch(prediction -> prediction.getPredictedWaitingTime() >= 0));
        verify(predictionClient, atLeast(3)).predict(any());
    }

    @Test
    void repeatedUnchangedRecalculationDoesNotDuplicatePredictionHistory() throws Exception {
        when(predictionClient.predict(any()))
                .thenReturn(new MlWaitingTimeResponse(12.34));

        createToken();
        int before = predictionRepository.readAll().size();

        getQueue();
        getQueue();

        assertEquals(before, predictionRepository.readAll().size());
    }

    @Test
    void cancellationAndCounterChangesTriggerFreshMlPredictions() throws Exception {
        when(predictionClient.predict(any()))
                .thenReturn(new MlWaitingTimeResponse(15.25));

        String firstTokenId = readId(createToken());
        createToken();
        int beforeCancellation = predictionRepository.readAll().size();

        mockMvc.perform(delete("/api/tokens/{id}", firstTokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());
        assertTrue(predictionRepository.readAll().size() > beforeCancellation);

        int beforeCounterChange = predictionRepository.readAll().size();
        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-2")
                        .header(HttpHeaders.AUTHORIZATION, authorization(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk());
        assertTrue(predictionRepository.readAll().size() > beforeCounterChange);
    }

    @Test
    void unavailableMlServiceUsesFallbackAndKeepsQueueAvailable() throws Exception {
        when(predictionClient.predict(any()))
                .thenThrow(new RuntimeException("ML service offline"));

        createToken();
        createToken();

        getQueue()
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitTime").value(0))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(5));

        assertTrue(predictionRepository.readAll().stream()
                .anyMatch(prediction -> "FALLBACK".equals(prediction.getSource())));
    }

    private ResultActions getQueue() throws Exception {
        return mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ID)
                .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());
    }

    private MvcResult createToken() throws Exception {
        return mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + SERVICE_ID + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private User user(String id, UserRole role) {
        return User.builder()
                .id(id)
                .name(role.name() + " ML User")
                .email(id + "@test.com")
                .password("not-used-in-jwt-test")
                .role(role)
                .build();
    }

    private String authorization(String userId, UserRole role) {
        return "Bearer " + jwtService.generateToken(User.builder()
                .id(userId)
                .email(userId + "@test.com")
                .role(role)
                .build());
    }
}
