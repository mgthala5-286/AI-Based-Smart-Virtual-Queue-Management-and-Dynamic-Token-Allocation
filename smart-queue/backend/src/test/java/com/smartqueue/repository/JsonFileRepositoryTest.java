package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private JsonFileRepository<Service> repository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        repository = new JsonFileRepository<>(
                objectMapper,
                temporaryDirectory.resolve("services.json"),
                Service.class,
                Service::getId);
    }

    @Test
    void saveStoresDataInTheJsonFile() throws Exception {
        Service service = service("service-1", "General Service");

        repository.save(service);

        assertEquals(List.of(service), repository.readAll());
        assertTrue(Files.readString(temporaryDirectory.resolve("services.json")).contains("General Service"));
    }

    @Test
    void readReturnsDataFromTheJsonFile() throws Exception {
        Service service = service("service-1", "General Service");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Files.writeString(
                temporaryDirectory.resolve("services.json"),
                objectMapper.writeValueAsString(List.of(service)));

        assertEquals(service, repository.findById("service-1").orElseThrow());
    }

    @Test
    void updateReplacesExistingData() {
        repository.save(service("service-1", "General Service"));
        Service updated = service("service-1", "Updated Service");

        repository.update(updated);

        assertEquals(updated, repository.findById("service-1").orElseThrow());
    }

    @Test
    void deleteRemovesExistingData() {
        repository.save(service("service-1", "General Service"));

        assertTrue(repository.delete("service-1"));
        assertTrue(repository.readAll().isEmpty());
        assertFalse(repository.delete("service-1"));
    }

    private Service service(String id, String name) {
        return Service.builder()
                .id(id)
                .name(name)
                .description("A test service")
                .averageServiceTime(10)
                .active(true)
                .build();
    }
}
