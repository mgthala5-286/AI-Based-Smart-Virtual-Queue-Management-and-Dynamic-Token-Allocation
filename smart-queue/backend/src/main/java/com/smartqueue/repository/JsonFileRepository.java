package com.smartqueue.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.exception.JsonDatabaseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Small JSON-file repository used as the Phase 1 internal database.
 *
 * @param <T> type stored in the JSON array
 */
public class JsonFileRepository<T> {

    private final ObjectMapper objectMapper;
    private final Path filePath;
    private final Class<T> entityType;
    private final Function<T, String> idExtractor;

    public JsonFileRepository(
            ObjectMapper objectMapper,
            Path filePath,
            Class<T> entityType,
            Function<T, String> idExtractor) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        ensureFileExists();
    }

    public synchronized List<T> readAll() {
        ensureFileExists();

        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return new ArrayList<>();
            }

            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, entityType);
            List<T> entities = objectMapper.readValue(json, listType);
            return entities == null ? new ArrayList<>() : new ArrayList<>(entities);
        } catch (IOException exception) {
            throw new JsonDatabaseException("Could not read JSON database file: " + filePath, exception);
        }
    }

    public synchronized Optional<T> findById(String id) {
        return readAll().stream()
                .filter(entity -> Objects.equals(id, idExtractor.apply(entity)))
                .findFirst();
    }

    public synchronized T save(T entity) {
        String id = requireId(entity);
        List<T> entities = readAll();

        if (entities.stream().anyMatch(existing -> Objects.equals(id, idExtractor.apply(existing)))) {
            throw new JsonDatabaseException("An entity with id '" + id + "' already exists in " + filePath);
        }

        entities.add(entity);
        writeAll(entities);
        return entity;
    }

    public synchronized T update(T entity) {
        String id = requireId(entity);
        List<T> entities = readAll();

        for (int index = 0; index < entities.size(); index++) {
            if (Objects.equals(id, idExtractor.apply(entities.get(index)))) {
                entities.set(index, entity);
                writeAll(entities);
                return entity;
            }
        }

        throw new JsonDatabaseException("Could not update entity with id '" + id + "' because it does not exist");
    }

    public synchronized boolean delete(String id) {
        List<T> entities = readAll();
        boolean removed = entities.removeIf(entity -> Objects.equals(id, idExtractor.apply(entity)));

        if (removed) {
            writeAll(entities);
        }

        return removed;
    }

    private String requireId(T entity) {
        if (entity == null) {
            throw new JsonDatabaseException("Cannot store a null entity in " + filePath);
        }

        String id = idExtractor.apply(entity);
        if (id == null || id.isBlank()) {
            throw new JsonDatabaseException("Entity id must not be null or blank");
        }
        return id;
    }

    private void ensureFileExists() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (!Files.exists(filePath)) {
                Files.writeString(
                        filePath,
                        "[]" + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException exception) {
            throw new JsonDatabaseException("Could not create JSON database file: " + filePath, exception);
        }
    }

    private void writeAll(List<T> entities) {
        try {
            String formattedJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entities);
            Files.writeString(
                    filePath,
                    formattedJson + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new JsonDatabaseException("Could not write JSON database file: " + filePath, exception);
        }
    }
}
