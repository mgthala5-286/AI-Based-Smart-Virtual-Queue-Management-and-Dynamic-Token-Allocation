package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.QueueEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Repository
public class QueueEventRepository {

    private final JsonFileRepository<QueueEvent> repository;

    public QueueEventRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("queue_events.json"),
                QueueEvent.class,
                QueueEvent::getId);
    }

    public List<QueueEvent> readAll() {
        return repository.readAll();
    }

    public Optional<QueueEvent> findById(String id) {
        return repository.findById(id);
    }

    public QueueEvent save(QueueEvent queueEvent) {
        return repository.save(queueEvent);
    }

    public QueueEvent update(QueueEvent queueEvent) {
        return repository.update(queueEvent);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
