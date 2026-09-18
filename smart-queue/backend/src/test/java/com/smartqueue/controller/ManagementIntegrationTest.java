package com.smartqueue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.User;
import com.smartqueue.model.UserRole;
import com.smartqueue.repository.CounterRepository;
import com.smartqueue.repository.ServiceRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-queue.database-path=target/test-management-data",
        "smart-queue.demo-accounts.enabled=false"
})
class ManagementIntegrationTest {

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

    @BeforeEach
    void clearManagementData() {
        counterRepository.readAll().forEach(counter -> counterRepository.delete(counter.getId()));
        serviceRepository.readAll().forEach(service -> serviceRepository.delete(service.getId()));
    }

    @Test
    void adminCanCreateService() throws Exception {
        mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Birth Certificate", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCanUpdateService() throws Exception {
        String serviceId = createService("Original Service", 10);

        mockMvc.perform(put("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Service",
                                  "description": "Updated description",
                                  "averageServiceTime": 15,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Service"))
                .andExpect(jsonPath("$.averageServiceTime").value(15));
    }

    @Test
    void adminCanSoftDeleteService() throws Exception {
        String serviceId = createService("Service To Deactivate", 10);

        mockMvc.perform(delete("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, userAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminCanViewAllServices() throws Exception {
        createService("Active Service", 10);
        String inactiveId = createService("Inactive Service", 20);
        deactivateService(inactiveId);

        mockMvc.perform(get("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Active Service"))
                .andExpect(jsonPath("$[1].name").value("Inactive Service"));
    }

    @Test
    void userCanViewActiveServicesOnly() throws Exception {
        createService("Public Service", 10);
        String inactiveId = createService("Hidden Service", 20);
        deactivateService(inactiveId);

        mockMvc.perform(get("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, userAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Public Service"));
    }

    @Test
    void cannotCreateCounterForNonexistentService() throws Exception {
        mockMvc.perform(post("/api/admin/counters")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJson(1, "missing-service")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCreateCounterForInactiveService() throws Exception {
        String serviceId = createService("Inactive Counter Service", 10);
        deactivateService(serviceId);

        mockMvc.perform(post("/api/admin/counters")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJson(1, serviceId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCounterNumberIsForbiddenForOneService() throws Exception {
        String serviceId = createService("Duplicate Counter Service", 10);
        createCounter(1, serviceId);

        mockMvc.perform(post("/api/admin/counters")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJson(1, serviceId)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanCreateCounter() throws Exception {
        String serviceId = createService("Counter Service", 10);

        mockMvc.perform(post("/api/admin/counters")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJson(3, serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.counterNumber").value(3))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void userCanViewActiveCountersOnly() throws Exception {
        String serviceId = createService("Counter Visibility Service", 10);
        String activeCounterId = createCounter(1, serviceId);
        String inactiveCounterId = createCounter(2, serviceId);
        deactivateCounter(inactiveCounterId);

        mockMvc.perform(get("/api/services/{serviceId}/counters", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, userAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(activeCounterId));
    }

    @Test
    void softDeletedCounterDoesNotAppearInPublicCounters() throws Exception {
        String serviceId = createService("Deleted Counter Service", 10);
        String counterId = createCounter(1, serviceId);
        deactivateCounter(counterId);

        mockMvc.perform(get("/api/services/{serviceId}/counters", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, userAuthorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void cannotUpdateCounterToInactiveService() throws Exception {
        String originalServiceId = createService("Original Counter Service", 10);
        String inactiveServiceId = createService("Inactive Target Service", 10);
        String counterId = createCounter(1, originalServiceId);
        deactivateService(inactiveServiceId);

        mockMvc.perform(put("/api/admin/counters/{id}", counterId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJsonWithActive(2, inactiveServiceId, true)))
                .andExpect(status().isBadRequest());
    }

    private String createService(String name, int averageServiceTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/services")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson(name, averageServiceTime)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private String createCounter(int counterNumber, String serviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/counters")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(counterJson(counterNumber, serviceId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private void deactivateService(String serviceId) throws Exception {
        mockMvc.perform(delete("/api/admin/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization()))
                .andExpect(status().isOk());
    }

    private void deactivateCounter(String counterId) throws Exception {
        mockMvc.perform(delete("/api/admin/counters/{id}", counterId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization()))
                .andExpect(status().isOk());
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private String serviceJson(String name, int averageServiceTime) {
        return "{\"name\":\"" + name + "\",\"description\":\"Test description\","
                + "\"averageServiceTime\":" + averageServiceTime + "}";
    }

    private String counterJson(int counterNumber, String serviceId) {
        return counterJsonWithActive(counterNumber, serviceId, true)
                .replace(",\"active\":true", "");
    }

    private String counterJsonWithActive(int counterNumber, String serviceId, boolean active) {
        return "{\"counterNumber\":" + counterNumber + ",\"serviceId\":\""
                + serviceId + "\",\"active\":" + active + "}";
    }

    private String adminAuthorization() {
        return "Bearer " + jwtService.generateToken(User.builder()
                .id("admin-test")
                .email("admin@test.com")
                .role(UserRole.ADMIN)
                .build());
    }

    private String userAuthorization() {
        return "Bearer " + jwtService.generateToken(User.builder()
                .id("user-test")
                .email("user@test.com")
                .role(UserRole.USER)
                .build());
    }
}
