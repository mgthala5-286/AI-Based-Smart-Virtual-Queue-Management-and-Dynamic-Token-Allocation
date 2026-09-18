package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Prediction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class PredictionRepository {

    private final JsonFileRepository<Prediction> repository;

    public PredictionRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("predictions.json"),
                Prediction.class,
                Prediction::getId);
    }

    public List<Prediction> readAll() {
        return repository.readAll();
    }

    public Optional<Prediction> findById(String id) {
        return repository.findById(id);
    }

    public Optional<Prediction> findLatestByTokenId(String tokenId) {
        return readAll().stream()
                .filter(prediction -> tokenId.equals(prediction.getTokenId()))
                .max(Comparator.comparing(
                        Prediction::getPredictionTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public Prediction save(Prediction prediction) {
        return repository.save(prediction);
    }

    public Prediction update(Prediction prediction) {
        return repository.update(prediction);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
