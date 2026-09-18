# Smart Queue Architecture

## Scope

This document describes the final Phase 1–9 implementation. Phase 10 adds documentation and validation only; it does not add runtime features.

## System context

```text
User browser
  │  HTML/CSS/JavaScript, JWT in localStorage
  │  http://localhost:5500
  ▼
Spring Boot Java backend
  │  http://localhost:8080
  ├── JWT authentication and role authorization
  ├── queue, token, service, and counter services
  ├── dynamic FIFO allocation
  ├── optional ML prediction client ───────► FastAPI Random Forest service
  │                                          http://localhost:8000
  ▼
JSON files in data/
```

The frontend never calls the Python service directly. Java owns authorization, queue state, fallback behavior, and persistence.

## Backend modules

| Module | Responsibility |
|---|---|
| `config` | Spring Security, JWT filter, CORS, Jackson, demo-account initialization, ML client settings |
| `controller` | HTTP endpoints and request authentication context |
| `service` | Business rules for authentication, management, tokens, queues, allocation, and ML fallback |
| `repository` | JSON file persistence through `JsonFileRepository<T>` |
| `model` | Users, services, counters, tokens, events, predictions, and queue state |
| `dto` | API request and response contracts |
| `exception` | Domain errors and one global JSON error response handler |

## Main request flows

### Token creation

1. A USER submits `POST /api/tokens` with a service ID.
2. `TokenService` validates the authenticated user and active service.
3. A service-prefixed token number is generated and persisted as `WAITING`.
4. A `TOKEN_CREATED` event is written.
5. `DynamicTokenAllocationService` recalculates the service queue.
6. The response returns the token’s current position and estimated wait.

### Queue refresh

1. The frontend requests `GET /api/queue/{serviceId}`.
2. Java recalculates the selected service before returning the active queue.
3. Active tokens are returned in FIFO order with position, status, and wait time.

### Waiting-time prediction

1. Java builds a request from queue size, tokens ahead, active counters, average service time, and hour of day.
2. If the ML client is enabled and reachable, Java calls the FastAPI prediction endpoint.
3. Valid predictions are rounded and stored in `predictions.json`.
4. Invalid or unavailable predictions use the deterministic fallback formula.

## Persistence files

| File | Contents |
|---|---|
| `users.json` | BCrypt-password users and roles |
| `services.json` | Service definitions and active state |
| `counters.json` | Service counter definitions and active state |
| `tokens.json` | Virtual tokens and current queue fields |
| `queue_events.json` | Creation, cancellation, and position-change events |
| `predictions.json` | Latest persisted ML/fallback prediction observations |

`JsonFileRepository<T>` creates missing directories/files, reads empty files safely, maps JSON using Jackson, and writes readable pretty-printed JSON.

## Security model

- Registration and login are public.
- JWT authentication is stateless.
- Passwords are BCrypt-hashed before persistence.
- USER accounts create and manage their own tokens.
- STAFF can inspect queues and elevated token information.
- ADMIN manages services and counters.
- The browser stores only JWT, user ID, role, and display name; it never stores a password.

## Runtime boundaries

The local system uses three independent processes:

1. Spring Boot backend on port 8080.
2. Optional FastAPI ML service on port 8000.
3. Static frontend server on port 5500.

The backend remains usable when the ML service is stopped because deterministic fallback waiting-time calculation is built into the Java service.

