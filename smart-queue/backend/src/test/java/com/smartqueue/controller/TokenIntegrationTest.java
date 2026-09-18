package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Service;
import com.smartqueue.model.TokenStatus;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
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
        "smart-queue.database-path=target/test-token-data",
        "smart-queue.demo-accounts.enabled=false"
})
class TokenIntegrationTest {

    private static final String USER_ID = "token-user";
    private static final String OTHER_USER_ID = "other-user";
    private static final String STAFF_ID = "token-staff";
    private static final String ADMIN_ID = "token-admin";
    private static final String ACTIVE_SERVICE_ID = "service-1";
    private static final String INACTIVE_SERVICE_ID = "inactive-service";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private QueueEventRepository queueEventRepository;

    @BeforeEach
    void clearAndSeedData() {
        queueEventRepository.readAll().forEach(event -> queueEventRepository.delete(event.getId()));
        tokenRepository.readAll().forEach(token -> tokenRepository.delete(token.getId()));
        serviceRepository.readAll().forEach(service -> serviceRepository.delete(service.getId()));
        userRepository.readAll().forEach(user -> userRepository.delete(user.getId()));

        serviceRepository.save(Service.builder()
                .id(ACTIVE_SERVICE_ID)
                .name("General Service")
                .description("Test service")
                .averageServiceTime(10)
                .active(true)
                .build());
        serviceRepository.save(Service.builder()
                .id(INACTIVE_SERVICE_ID)
                .name("Inactive Service")
                .description("Inactive test service")
                .averageServiceTime(10)
                .active(false)
                .build());

        userRepository.save(user(USER_ID, UserRole.USER));
        userRepository.save(user(OTHER_USER_ID, UserRole.USER));
        userRepository.save(user(STAFF_ID, UserRole.STAFF));
        userRepository.save(user(ADMIN_ID, UserRole.ADMIN));
    }

    @Test
    void userCanCreateWaitingToken() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(ACTIVE_SERVICE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tokenNumber").value("G001"))
                .andExpect(jsonPath("$.serviceId").value(ACTIVE_SERVICE_ID))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.estimatedWaitTime").value(0));
    }

    @Test
    void invalidServiceIsRejected() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("missing-service")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveServiceIsRejected() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(INACTIVE_SERVICE_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenIsStoredInTokensJson() throws Exception {
        String tokenId = readId(createToken(USER_ID));

        var storedToken = tokenRepository.findById(tokenId).orElse(null);
        assertNotNull(storedToken);
        assertEquals(USER_ID, storedToken.getUserId());
        assertEquals(ACTIVE_SERVICE_ID, storedToken.getServiceId());
        assertEquals(TokenStatus.WAITING, storedToken.getStatus());
    }

    @Test
    void tokenOwnerCanRetrieveToken() throws Exception {
        String tokenId = readId(createToken(USER_ID));

        mockMvc.perform(get("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tokenId))
                .andExpect(jsonPath("$.tokenNumber").value("G001"));
    }

    @Test
    void userCanRetrieveOwnTokens() throws Exception {
        createToken(USER_ID);

        mockMvc.perform(get("/api/users/{userId}/tokens", USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("WAITING"));
    }

    @Test
    void ownerCanCancelTokenWithoutDeletingIt() throws Exception {
        String tokenId = readId(createToken(USER_ID));

        mockMvc.perform(delete("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(TokenStatus.CANCELLED,
                tokenRepository.findById(tokenId).orElseThrow().getStatus());
    }

    @Test
    void tokenCreationAndCancellationCreateQueueEvents() throws Exception {
        String tokenId = readId(createToken(USER_ID));
        assertEquals(2, queueEventRepository.readAll().size());
        assertEquals("TOKEN_CREATED", queueEventRepository.readAll().get(0).getEventType());
        assertTrue(queueEventRepository.readAll().stream().anyMatch(event ->
                "POSITION_CHANGED".equals(event.getEventType())
                        && tokenId.equals(event.getTokenId())
                        && event.getOldPosition() == 0
                        && event.getNewPosition() == 1));

        mockMvc.perform(delete("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(USER_ID, UserRole.USER)))
                .andExpect(status().isOk());

        assertEquals(4, queueEventRepository.readAll().size());
        assertTrue(queueEventRepository.readAll().stream().anyMatch(event ->
                "TOKEN_CANCELLED".equals(event.getEventType())
                        && tokenId.equals(event.getTokenId())));
        assertTrue(queueEventRepository.readAll().stream().anyMatch(event ->
                "POSITION_CHANGED".equals(event.getEventType())
                        && tokenId.equals(event.getTokenId())
                        && event.getOldPosition() == 1
                        && event.getNewPosition() == 0));
    }

    @Test
    void tokenAuthorizationIsEnforced() throws Exception {
        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(ACTIVE_SERVICE_ID)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(STAFF_ID, UserRole.STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(ACTIVE_SERVICE_ID)))
                .andExpect(status().isForbidden());

        String tokenId = readId(createToken(USER_ID));

        mockMvc.perform(get("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(OTHER_USER_ID, UserRole.USER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tokens/{id}", tokenId)
                        .header(HttpHeaders.AUTHORIZATION, authorization(STAFF_ID, UserRole.STAFF)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/{userId}/tokens", USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, authorization(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    private MvcResult createToken(String userId) throws Exception {
        return mockMvc.perform(post("/api/tokens")
                        .header(HttpHeaders.AUTHORIZATION, authorization(userId, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(ACTIVE_SERVICE_ID)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private User user(String id, UserRole role) {
        return User.builder()
                .id(id)
                .name(role.name() + " Test User")
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

    private String tokenRequest(String serviceId) {
        return "{\"serviceId\":\"" + serviceId + "\"}";
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }
}
