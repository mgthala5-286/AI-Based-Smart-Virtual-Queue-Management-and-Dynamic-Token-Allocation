package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRepository {

    private final JsonFileRepository<Service> repository;

    public ServiceRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("services.json"),
                Service.class,
                Service::getId);
    }

    public List<Service> readAll() {
        return repository.readAll();
    }

    public Optional<Service> findById(String id) {
        return repository.findById(id);
    }

    public Service save(Service service) {
        return repository.save(service);
    }

    public Service update(Service service) {
        return repository.update(service);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
