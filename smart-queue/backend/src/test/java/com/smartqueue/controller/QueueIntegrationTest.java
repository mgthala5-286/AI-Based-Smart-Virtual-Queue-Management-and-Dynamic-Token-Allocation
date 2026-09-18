package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Counter;
import com.smartqueue.model.QueueEvent;
import com.smartqueue.model.Service;
import com.smartqueue.model.Token;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.QueueEventRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.TokenRepository;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-queue.database-path=target/test-queue-data",
        "smart-queue.demo-accounts.enabled=false"
})
class QueueIntegrationTest {

    private static final String USER_ID = "queue-user";
    private static final String OTHER_USER_ID = "queue-other-user";
    private static final String STAFF_ID = "queue-staff";
    private static final String ADMIN_ID = "queue-admin";
    private static final String GENERAL_SERVICE_ID = "service-1";
    private static final String DESK_SERVICE_ID = "service-2";

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
    private UserRepository userRepository;

    @BeforeEach
    void clearAndSeedData() {
        queueEventRepository.readAll().forEach(event -> queueEventRepository.delete(event.getId()));
        tokenRepository.readAll().forEach(token -> tokenRepository.delete(token.getId()));
        counterRepository.readAll().forEach(counter -> counterRepository.delete(counter.getId()));
        serviceRepository.readAll().forEach(service -> serviceRepository.delete(service.getId()));
        userRepository.readAll().forEach(user -> userRepository.delete(user.getId()));

        serviceRepository.save(Service.builder()
                .id(GENERAL_SERVICE_ID)
                .name("General Service")
                .description("General queue")
                .averageServiceTime(10)
                .active(true)
                .build());
        serviceRepository.save(Service.builder()
                .id(DESK_SERVICE_ID)
                .name("Desk Service")
                .description("Desk queue")
                .averageServiceTime(20)
                .active(true)
                .build());

        counterRepository.save(Counter.builder()
                .id("counter-1")
                .counterNumber(1)
                .serviceId(GENERAL_SERVICE_ID)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-2")
                .counterNumber(2)
                .serviceId(GENERAL_SERVICE_ID)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-3")
                .counterNumber(1)
                .serviceId(DESK_SERVICE_ID)
                .active(true)
                .build());

        userRepository.save(user(USER_ID, UserRole.USER));
        userRepository.save(user(OTHER_USER_ID, UserRole.USER));
        userRepository.save(user(STAFF_ID, UserRole.STAFF));
        userRepository.save(user(ADMIN_ID, UserRole.ADMIN));
    }

    @Test
    void fifoQueueAndWaitingTimesUseActiveCounters() throws Exception {
        createToken(USER_ID, GENERAL_SERVICE_ID);
        createToken(USER_ID, GENERAL_SERVICE_ID);
        createToken(USER_ID, GENERAL_SERVICE_ID);

        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].tokenNumber").value("G001"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitTime").value(0))
                .andExpect(jsonPath("$[1].tokenNumber").value("G002"))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(5))
                .andExpect(jsonPath("$[2].tokenNumber").value("G003"))
                .andExpect(jsonPath("$[2].position").value(3))
                .andExpect(jsonPath("$[2].estimatedWaitTime").value(10));

        mockMvc.perform(get("/api/queue/{serviceId}/size", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(GENERAL_SERVICE_ID))
                .andExpect(jsonPath("$.queueSize").value(3));
    }

    @Test
    void cancellationRecalculatesPositionsAndRecordsPositionChanges() throws Exception {
        String firstId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));
        String secondId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));
        String thirdId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));

        mockMvc.perform(delete("/api/tokens/{id}", secondId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].tokenNumber").value("G001"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[1].tokenNumber").value("G003"))
                .andExpect(jsonPath("$[1].position").value(2));

        assertEquals(1, tokenRepository.findById(firstId).orElseThrow().getQueuePosition());
        assertEquals(0, tokenRepository.findById(secondId).orElseThrow().getQueuePosition());
        assertEquals(2, tokenRepository.findById(thirdId).orElseThrow().getQueuePosition());
        assertTrue(queueEventRepository.readAll().stream().anyMatch(event ->
                "POSITION_CHANGED".equals(event.getEventType())
                        && thirdId.equals(event.getTokenId())
                        && event.getOldPosition() == 3
                        && event.getNewPosition() == 2));
    }

    @Test
    void serviceQueuesAreIndependent() throws Exception {
        String generalTokenId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));
        String deskTokenId = readId(createToken(USER_ID, DESK_SERVICE_ID));

        mockMvc.perform(delete("/api/tokens/{id}", generalTokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queue/{serviceId}/position/{tokenId}", DESK_SERVICE_ID, deskTokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.peopleAhead").value(0));

        mockMvc.perform(get("/api/queue/{serviceId}/size", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueSize").value(0));
    }

    @Test
    void detailedPositionRequiresOwnershipUnlessStaffOrAdmin() throws Exception {
        String tokenId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));

        mockMvc.perform(get("/api/queue/{serviceId}/position/{tokenId}", GENERAL_SERVICE_ID, tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(OTHER_USER_ID, UserRole.USER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/queue/{serviceId}/position/{tokenId}", GENERAL_SERVICE_ID, tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(STAFF_ID, UserRole.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.peopleAhead").value(0));

        mockMvc.perform(get("/api/queue/{serviceId}/position/{tokenId}", DESK_SERVICE_ID, tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(STAFF_ID, UserRole.STAFF)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void waitingTimeUsesRemainingActiveCounterCount() throws Exception {
        createToken(USER_ID, GENERAL_SERVICE_ID);
        createToken(USER_ID, GENERAL_SERVICE_ID);
        String thirdId = readId(createToken(USER_ID, GENERAL_SERVICE_ID));

        counterRepository.update(Counter.builder()
                .id("counter-2")
                .counterNumber(2)
                .serviceId(GENERAL_SERVICE_ID)
                .active(false)
                .build());

        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].tokenNumber").value("G003"))
                .andExpect(jsonPath("$[2].estimatedWaitTime").value(20));

        assertEquals(20, tokenRepository.findById(thirdId).orElseThrow().getEstimatedWaitTime());
    }

    @Test
    void terminalTokensAreExcludedFromTheActiveQueue() throws Exception {
        createToken(USER_ID, GENERAL_SERVICE_ID);
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        tokenRepository.save(Token.builder()
                .id("completed-token")
                .tokenNumber("G099")
                .userId(USER_ID)
                .serviceId(GENERAL_SERVICE_ID)
                .status(TokenStatus.COMPLETED)
                .createdAt(now)
                .queuePosition(0)
                .estimatedWaitTime(0)
                .build());
        tokenRepository.save(Token.builder()
                .id("called-token")
                .tokenNumber("G100")
                .userId(USER_ID)
                .serviceId(GENERAL_SERVICE_ID)
                .status(TokenStatus.CALLED)
                .createdAt(now.plusSeconds(1))
                .queuePosition(0)
                .estimatedWaitTime(0)
                .build());

        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[1].status").value("CALLED"));
    }

    @Test
    void repeatedQueueReadsDoNotCreateDuplicatePositionEvents() throws Exception {
        createToken(USER_ID, GENERAL_SERVICE_ID);
        long before = positionChangedEvents();

        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());

        assertEquals(before, positionChangedEvents());
    }

    @Test
    void queueRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/queue/{serviceId}", GENERAL_SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult createToken(String userId, String serviceId) throws Exception {
        return mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(userId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + serviceId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private User user(String id, UserRole role) {
        return User.builder()
                .id(id)
                .name(role.name() + " Queue User")
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

    private String readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private long positionChangedEvents() {
        return queueEventRepository.readAll().stream()
                .filter(event -> "POSITION_CHANGED".equals(event.getEventType()))
                .count();
    }
}
