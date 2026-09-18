package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JsonFileRepository<User> repository;

    public UserRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("users.json"),
                User.class,
                User::getId);
    }

    public List<User> readAll() {
        return repository.readAll();
    }

    public Optional<User> findById(String id) {
        return repository.findById(id);
    }

    public User save(User user) {
        return repository.save(user);
    }

    public User update(User user) {
        return repository.update(user);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
