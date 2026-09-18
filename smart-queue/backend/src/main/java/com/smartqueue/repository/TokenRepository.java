package com.smartqueue.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.model.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Repository
public class TokenRepository {

    private final JsonFileRepository<Token> repository;

    public TokenRepository(
            ObjectMapper objectMapper,
            @Value("${smart-queue.database-path:../data/}") String databasePath) {
        this.repository = new JsonFileRepository<>(
                objectMapper,
                Path.of(databasePath).resolve("tokens.json"),
                Token.class,
                Token::getId);
    }

    public List<Token> readAll() {
        return repository.readAll();
    }

    public Optional<Token> findById(String id) {
        return repository.findById(id);
    }

    public Token save(Token token) {
        return repository.save(token);
    }

    public Token update(Token token) {
        return repository.update(token);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }
}
