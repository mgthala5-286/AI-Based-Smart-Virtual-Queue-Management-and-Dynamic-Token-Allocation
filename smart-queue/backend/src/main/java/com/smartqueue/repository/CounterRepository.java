package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Counter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Repository
public class CounterRepository {

    private final JsonFileRepository<Counter> repository;

    public CounterRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("counters.json"),
                Counter.class,
                Counter::getId);
    }

    public List<Counter> readAll() {
        return repository.readAll();
    }

    public Optional<Counter> findById(String id) {
        return repository.findById(id);
    }

    public Counter save(Counter counter) {
        return repository.save(counter);
    }

    public Counter update(Counter counter) {
        return repository.update(counter);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
