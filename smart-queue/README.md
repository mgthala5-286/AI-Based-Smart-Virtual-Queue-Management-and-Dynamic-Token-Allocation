# AI-Based Smart Virtual Queue and Dynamic Token Allocation System

This repository contains Phases 1 through 10 of the Smart Queue project. The Java backend provides the JSON-file database foundation, stateless user registration/login with BCrypt and JWT, service/counter management, virtual token generation, FIFO queue management, dynamic token allocation, and optional integration with the Phase 7 Random Forest waiting-time service. The Phase 9 frontend is a responsive vanilla HTML/CSS/JavaScript client. Phase 10 contains final validation, documentation, demo guidance, and research/patent preparation. Notifications and production database work are intentionally not included.

## Technologies

- Java 21
- Spring Boot 3.3.5
- Spring Web
- Spring Security
- JJWT
- BCrypt password hashing
- Jakarta Bean Validation
- Jackson
- Lombok
- JUnit 5
- Mockito (provided by `spring-boot-starter-test`)
- Maven
- Python FastAPI ML service integration
- HTML, CSS, and vanilla JavaScript frontend

## How the JSON database works

The backend uses `JsonFileRepository<T>` instead of Spring Data or a SQL/NoSQL database. Each repository points to one JSON file under `../data/` when the application is run from the `backend` directory.

The repository can read all records, find a record by ID, save, update, and delete records. It creates the data directory and file automatically when needed, treats a missing or empty file as an empty list, and writes readable pretty-printed JSON.

The initial data contains one General Service with an average service time of 10 minutes and two active counters. On backend startup, `users.json` receives the development-only ADMIN and STAFF accounts if they do not already exist. User registration adds normal users with the default `USER` role. Passwords are stored as BCrypt hashes.

Service and counter management uses the same repositories. Admin create, list-all, update, and soft-delete operations write to `services.json` and `counters.json`. Public authenticated lists return active services and active counters only. Deleting a service or counter sets `active` to `false`; the JSON record remains available to administrators.

Token management stores new tokens in `tokens.json` and lifecycle records in `queue_events.json`. A USER can create a token for an active service. New tokens use a simple service-name prefix such as `G001`, start with `WAITING`, and are immediately placed in the FIFO queue. Token cancellation changes the status to `CANCELLED` without deleting the record.

Dynamic allocation recalculates active-token positions after token and counter changes. It preserves `createdAt ASC` FIFO order. Waiting times use the Phase 7 Random Forest service when enabled and reachable, with `peopleAhead * averageServiceTime / activeCounters` as the safe fallback. Every changed prediction is stored in `predictions.json` with source `ML` or `FALLBACK`.

## Authentication API

Public endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`

All other API paths require `Authorization: Bearer <JWT>`.

## Service and counter management API

Admin endpoints require an ADMIN JWT:

- `POST /api/admin/services`
- `GET /api/admin/services`
- `PUT /api/admin/services/{id}`
- `DELETE /api/admin/services/{id}` (soft delete)
- `POST /api/admin/counters`
- `GET /api/admin/counters`
- `PUT /api/admin/counters/{id}`
- `DELETE /api/admin/counters/{id}` (soft delete)

Authenticated USER, STAFF, and ADMIN accounts can use:

- `GET /api/services` for active services
- `GET /api/services/{serviceId}/counters` for active counters belonging to an active service

## Virtual token API

- `POST /api/tokens` — USER only; creates a `WAITING` token
- `GET /api/tokens/{id}` — token owner, STAFF, or ADMIN
- `GET /api/users/{userId}/tokens` — the user themselves, STAFF, or ADMIN
- `DELETE /api/tokens/{id}` — token owner, STAFF, or ADMIN; soft cancellation

Example token request:

```json
{
  "serviceId": "service-1"
}
```

## Queue API

Authenticated users can use the FIFO queue endpoints:

- `GET /api/queue/{serviceId}` — active queue ordered by creation time
- `GET /api/queue/{serviceId}/position/{tokenId}` — owner, STAFF, or ADMIN
- `GET /api/queue/{serviceId}/size` — active queue count

Queue positions remain FIFO. Waiting times use the configured ML service when available and fall back to `peopleAhead * averageServiceTime / activeCounters`. Queue position changes are stored as `POSITION_CHANGED` events in `queue_events.json`.

ML integration configuration is in `backend/src/main/resources/application.properties`:

```properties
ml.service.url=http://localhost:8000
ml.service.connect-timeout=2000
ml.service.read-timeout=3000
ml.service.enabled=true
```

Start the Phase 7 ML service from `ml-service` before starting the backend:

```bash
python train.py
uvicorn app:app --reload --port 8000
```

ADMIN counter capacity endpoints:

- `POST /api/admin/counters/{id}/activate`
- `POST /api/admin/counters/{id}/deactivate`

Example service request:

```json
{
  "name": "General Service",
  "description": "Customer support",
  "averageServiceTime": 10
}
```

Example counter request:

```json
{
  "counterNumber": 1,
  "serviceId": "service-id-from-the-service-response"
}
```

Example registration:

```json
{
  "name": "John",
  "email": "john@test.com",
  "password": "password123"
}
```

Example login:

```json
{
  "email": "john@test.com",
  "password": "password123"
}
```

Development accounts:

- ADMIN: `admin@smartqueue.com` / `admin123`
- STAFF: `staff@smartqueue.com` / `staff123`

Change `smart-queue.jwt-secret` in `application.properties` outside local development.

## Project structure

```text
smart-queue/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/smartqueue/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── exception/
│       │   ├── model/
│       │   ├── repository/
│       │   └── service/
│       └── test/
├── data/
│   ├── users.json
│   ├── services.json
│   ├── counters.json
│   ├── tokens.json
│   ├── queue_events.json
│   └── predictions.json
├── frontend/
│   ├── index.html
│   ├── login.html
│   ├── register.html
│   ├── user-dashboard.html
│   ├── staff-dashboard.html
│   ├── admin-dashboard.html
│   ├── css/style.css
│   └── js/
├── docs/
│   ├── architecture.md
│   ├── algorithm.md
│   ├── ml-results-and-limitations.md
│   ├── demo-guide.md
│   ├── viva-questions.md
│   ├── patent-research-preparation.md
│   └── final-validation-report.md
└── ml-service/
```

## Run the backend

From the backend directory:

```bash
cd smart-queue/backend
mvn spring-boot:run
```

The database path can be changed in `backend/src/main/resources/application.properties` with `smart-queue.database-path`.

## Run the full local system

Start the Java backend from `smart-queue/backend`:

```bash
mvn spring-boot:run
```

Start the existing Phase 7 ML service from `smart-queue/ml-service`:

```bash
python train.py
uvicorn app:app --reload --port 8000
```

Serve the Phase 9 frontend from `smart-queue/frontend` with a simple local server:

```bash
python -m http.server 5500
```

Open [http://localhost:5500](http://localhost:5500). The frontend communicates only with the Java backend at [http://localhost:8080](http://localhost:8080); it never calls the Python service directly. CORS is restricted to the local frontend origins `http://localhost:5500` and `http://127.0.0.1:5500`.

The frontend provides:

- User registration, login, token creation, queue position, polling, and cancellation.
- Staff read-only queue monitoring.
- Admin service and counter management using the existing backend APIs.
- JWT storage only in browser `localStorage`; passwords are never stored.

## Test the health endpoint

With the application running, open:

```text
http://localhost:8080/api/health
```

It returns:

```json
{
  "status": "UP",
  "application": "Smart Queue"
}
```

The same endpoint can be tested from a terminal:

```bash
curl http://localhost:8080/api/health
```

## Run tests

From `smart-queue/backend`:

```bash
mvn test
```

The final recorded result is 63 tests passed with zero failures, errors, or skipped tests. See [docs/final-validation-report.md](docs/final-validation-report.md) for the final checklist.

## Final documentation

- [Architecture](docs/architecture.md)
- [Queue and waiting-time algorithms](docs/algorithm.md)
- [ML results and limitations](docs/ml-results-and-limitations.md)
- [Demo guide](docs/demo-guide.md)
- [Viva questions and answers](docs/viva-questions.md)
- [Patent and research-paper preparation](docs/patent-research-preparation.md)
