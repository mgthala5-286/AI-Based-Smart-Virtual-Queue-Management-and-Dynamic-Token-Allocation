package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Counter;
import com.smartqueue.model.QueueState;
import com.smartqueue.model.Service;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.QueueEventRepository;
import com.smartqueue.repository.ServiceRepository;
import com.smartqueue.repository.TokenRepository;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.service.JwtService;
import com.smartqueue.service.QueueStateService;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-queue.database-path=target/test-dynamic-allocation-data",
        "smart-queue.demo-accounts.enabled=false"
})
class DynamicAllocationIntegrationTest {

    private static final String USER_ID = "dynamic-user";
    private static final String OTHER_USER_ID = "dynamic-other-user";
    private static final String STAFF_ID = "dynamic-staff";
    private static final String ADMIN_ID = "dynamic-admin";
    private static final String SERVICE_ONE_ID = "service-1";
    private static final String SERVICE_TWO_ID = "service-2";

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

    @Autowired
    private QueueStateService queueStateService;

    @BeforeEach
    void clearAndSeedData() {
        queueEventRepository.readAll().forEach(event -> queueEventRepository.delete(event.getId()));
        tokenRepository.readAll().forEach(token -> tokenRepository.delete(token.getId()));
        counterRepository.readAll().forEach(counter -> counterRepository.delete(counter.getId()));
        serviceRepository.readAll().forEach(service -> serviceRepository.delete(service.getId()));
        userRepository.readAll().forEach(user -> userRepository.delete(user.getId()));

        serviceRepository.save(Service.builder()
                .id(SERVICE_ONE_ID)
                .name("General Service")
                .description("General queue")
                .averageServiceTime(10)
                .active(true)
                .build());
        serviceRepository.save(Service.builder()
                .id(SERVICE_TWO_ID)
                .name("Desk Service")
                .description("Desk queue")
                .averageServiceTime(20)
                .active(true)
                .build());

        counterRepository.save(Counter.builder()
                .id("counter-1")
                .counterNumber(1)
                .serviceId(SERVICE_ONE_ID)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-2")
                .counterNumber(2)
                .serviceId(SERVICE_ONE_ID)
                .active(true)
                .build());
        counterRepository.save(Counter.builder()
                .id("counter-3")
                .counterNumber(1)
                .serviceId(SERVICE_TWO_ID)
                .active(true)
                .build());

        userRepository.save(user(USER_ID, UserRole.USER));
        userRepository.save(user(OTHER_USER_ID, UserRole.USER));
        userRepository.save(user(STAFF_ID, UserRole.STAFF));
        userRepository.save(user(ADMIN_ID, UserRole.ADMIN));
    }

    @Test
    void exactConsecutiveCancellationAndNewTokenScenarioRecalculatesQueue() throws Exception {
        List<String> tokenIds = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            tokenIds.add(readId(createToken(SERVICE_ONE_ID)));
        }

        cancelToken(tokenIds.get(2));
        cancelToken(tokenIds.get(0));
        createToken(SERVICE_ONE_ID);

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$[0].tokenNumber").value("G002"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitTime").value(0))
                .andExpect(jsonPath("$[1].tokenNumber").value("G004"))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(5))
                .andExpect(jsonPath("$[2].tokenNumber").value("G005"))
                .andExpect(jsonPath("$[2].position").value(3))
                .andExpect(jsonPath("$[2].estimatedWaitTime").value(10))
                .andExpect(jsonPath("$[3].tokenNumber").value("G006"))
                .andExpect(jsonPath("$[3].position").value(4))
                .andExpect(jsonPath("$[3].estimatedWaitTime").value(15));
    }

    @Test
    void counterDeactivationChangesWaitingTimesButPreservesFifoOrder() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);

        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-2")
                        .header(HttpHeaders.AUTHORIZATION, authorization(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tokenNumber").value("G001"))
                .andExpect(jsonPath("$[1].tokenNumber").value("G002"))
                .andExpect(jsonPath("$[2].tokenNumber").value("G003"))
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(10))
                .andExpect(jsonPath("$[2].estimatedWaitTime").value(20));
    }

    @Test
    void counterActivationRecalculatesWaitingTimesAgain() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);

        String adminAuth = authorization(ADMIN_ID, UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-2")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/counters/{id}/activate", "counter-2")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(5))
                .andExpect(jsonPath("$[2].estimatedWaitTime").value(10));
    }

    @Test
    void zeroActiveCountersAreSafeAndProduceZeroWaitingTimes() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);

        String adminAuth = authorization(ADMIN_ID, UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-1")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-2")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitTime").value(0))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitTime").value(0));
    }

    @Test
    void onlyAdminCanActivateOrDeactivateCounters() throws Exception {
        String userAuth = authorization(USER_ID, UserRole.USER);
        String staffAuth = authorization(STAFF_ID, UserRole.STAFF);

        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/counters/{id}/deactivate", "counter-1")
                        .header(HttpHeaders.AUTHORIZATION, staffAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/counters/{id}/activate", "counter-1")
                        .header(HttpHeaders.AUTHORIZATION, authorization(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void queueStateReportsCurrentQueueAndCounterCapacity() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);

        QueueState state = queueStateService.getCurrentState(SERVICE_ONE_ID);
        assertEquals(SERVICE_ONE_ID, state.getServiceId());
        assertEquals(2, state.getQueueSize());
        assertEquals(2, state.getActiveCounters());
        assertNotNull(state.getTimestamp());
    }

    @Test
    void repeatedDynamicRecalculationDoesNotDuplicateUnchangedPositionEvents() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);
        long before = positionChangedEvents();

        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/queue/{serviceId}", SERVICE_ONE_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());

        assertEquals(before, positionChangedEvents());
    }

    @Test
    void dynamicAllocationKeepsServicesIndependent() throws Exception {
        createToken(SERVICE_ONE_ID);
        createToken(SERVICE_ONE_ID);
        String deskTokenId = readId(createToken(SERVICE_TWO_ID));

        String firstGeneralTokenId = tokenRepository.readAll().stream()
                .filter(token -> SERVICE_ONE_ID.equals(token.getServiceId()))
                .findFirst()
                .orElseThrow()
                .getId();
        cancelToken(firstGeneralTokenId);

        mockMvc.perform(get("/api/queue/{serviceId}/position/{tokenId}", SERVICE_TWO_ID, deskTokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.peopleAhead").value(0));
    }

    private MvcResult createToken(String serviceId) throws Exception {
        return mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + serviceId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void cancelToken(String tokenId) throws Exception {
        mockMvc.perform(delete("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());
    }

    private User user(String id, UserRole role) {
        return User.builder()
                .id(id)
                .name(role.name() + " Dynamic User")
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
