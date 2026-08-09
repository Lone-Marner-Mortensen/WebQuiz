# WebQuiz

A REST API-based web quiz engine built with Spring Boot and Kotlin, structured as a hexagonal architecture (controller / domain / repository).  
Users can register, create multi-question quizzes, solve them, and track their completion history.

It only contains the backend API, no frontend is included.  
It runs locally with a PostgreSQL database in Docker, and all endpoints are documented (at run-time) via [Swagger UI](http://localhost:8080/swagger-ui.html).

## What's in This Project

This is a backend application providing:

- **User registration and authentication** — register with email/password, authenticate via HTTP Basic Auth.
- **Quiz management** — create multi-question quizzes (1–7 questions), retrieve a single quiz, list all quizzes (paginated), delete a quiz (author only).
- **Quiz solving** — submit one answer per question and get immediate feedback, with completions recorded to your account.
- **Completion tracking** — paginated history of quizzes a user has completed, tied to their account.
- **API documentation** — [Swagger UI](http://localhost:8080/swagger-ui.html) generated via springdoc and available at runtime.

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/register` | No | Register a new user |
| `POST` | `/api/quizzes` | Yes | Create a quiz |
| `GET` | `/api/quizzes` | Yes | List all quizzes (paginated, 10/page) |
| `GET` | `/api/quizzes/{id}` | Yes | Get a specific quiz |
| `POST` | `/api/quizzes/{id}/solve` | Yes | Submit answers; completion is recorded for the caller |
| `DELETE` | `/api/quizzes/{id}` | Yes | Delete a quiz (creator only) |
| `GET` | `/api/quizzes/completed` | Yes | Get current user's completion history (paginated, 10/page) |

## Project Architecture

```
src/main/kotlin/
├── WebQuizApplication.kt                 # Spring Boot entry point
├── ApiDocsConfig.kt                      # OpenAPI/Swagger metadata
├── quiz/
│   ├── controller/
│   │   ├── QuizController.kt             # Quiz CRUD and solving endpoints
│   │   ├── UserController.kt             # Registration endpoint
│   │   ├── dto/                          # Request/response DTOs
│   │   ├── mapper/                       # MapStruct mappers (domain <-> DTO)
│   │   └── validation/                   # Custom bean-validation annotations
│   ├── domain/
│   │   ├── Quiz.kt, Question.kt, User.kt, QuizCompletion.kt   # Domain models
│   │   ├── Id.kt                         # UUID id generation
│   │   ├── exception/                    # Domain exceptions
│   │   ├── response/                     # Domain-level result types
│   │   ├── repository/                   # Repository interfaces (ports)
│   │   └── service/                      # QuizService / UserService (business logic)
│   ├── repository/
│   │   ├── QuizRepositoryImpl.kt, UserRepositoryImpl.kt, QuizCompletionRepositoryImpl.kt
│   │   ├── dto/                          # JPA entities
│   │   ├── mapper/                       # Entity <-> domain mappers
│   │   └── jpa/adapters/                 # Spring Data JPA repository interfaces
│   ├── security/
│   │   ├── SecurityConfig.kt             # HTTP Basic Auth, BCrypt, stateless sessions
│   │   └── UserDetailsServiceAdapter.kt  # Spring Security integration
│   └── error/
│       ├── GlobalExceptionHandler.kt     # Centralized error responses
│       └── ErrorResponse.kt
```

### Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2.20 |
| Framework | Spring Boot 4.1 |
| JDK | Java 21 |
| Database | PostgreSQL 16, via Docker Compose |
| Schema migrations | Flyway |
| ORM | Hibernate JPA (schema-validated against Flyway, not auto-generated) |
| Security | Spring Security — HTTP Basic, BCrypt |
| API docs | springdoc-openapi (Swagger UI) |
| Object mapping | MapStruct |
| Monitoring | Spring Boot Actuator |
| Build | Gradle (wrapper included) |
| Testing | Testcontainers (real Postgres in integration tests) |

### Security

Only `/api/register` is public. Every other endpoint — creating, browsing, solving, deleting quizzes, and viewing completion history — requires HTTP Basic authentication. Quiz deletion is further restricted to the quiz's author (a non-author delete returns `403` with a `QUIZ_AUTHOR_MISMATCH` error body). Passwords are stored as BCrypt hashes. Sessions are stateless.

Note: `401 Unauthorized` responses (missing/invalid credentials) come from Spring Security's filter chain, which runs before requests reach a controller — these have an empty body (just a `WWW-Authenticate` header), unlike other error responses below.

### Database

PostgreSQL 16 running in Docker (`compose.yaml`, service `postgres`, database `webquiz`). Schema is managed by Flyway (`src/main/resources/db/migration/V1__init.sql`); Hibernate is configured with `ddl-auto=validate` and only checks that the entity mappings match the migrated schema — it never creates or alters tables itself. Data persists in a named Docker volume (`pgdata`) across container restarts.

In development, `spring-boot-docker-compose` auto-starts/detects the `compose.yaml` Postgres service and wires the datasource automatically — no connection URL or credentials are hardcoded in `application.properties`.

### Error Responses

Most failures return a JSON body via `GlobalExceptionHandler`:

```json
{"status": 404, "error": "QUIZ_NOT_FOUND", "message": "No quiz with id: ..."}
```

| Status | Error code | Cause |
|--------|-----------|-------|
| 400 | `VALIDATION_ERROR` | Bean validation failure (blank title, too many questions, etc.) |
| 400 | `MALFORMED_REQUEST` | Unparseable JSON body |
| 400 | `INVALID_ANSWER` | Invalid quiz data (e.g. answer index out of range) |
| 403 | `QUIZ_AUTHOR_MISMATCH` | Caller is not the quiz's author (on delete) |
| 404 | `QUIZ_NOT_FOUND` | No quiz with the given id |
| 409 | `DUPLICATE_EMAIL` | Email already registered |
| 409 | `DATA_INTEGRITY_VIOLATION` | Database constraint violation |
| 500 | `INTERNAL_SERVER_ERROR` | Unhandled exception |

`401 Unauthorized` is the one exception — it's returned by Spring Security before the request reaches a controller, so it has no JSON body.

## How to Build, Run and Stop

### Prerequisites

- JDK 21 or later
- Docker (for the Postgres container)

### Build

```bash
./gradlew build
```

### Run

Start Postgres and the app together with one command:

```bash
./gradlew start
```

This runs `docker compose up -d` followed by `./gradlew bootRun`. The server starts on **port 8080**. Press `Ctrl+C` to stop the app; Postgres keeps running.

### Stop

To stop everything (app + Postgres container):

```bash
./gradlew stop
```
Alternatively, send a POST request to the actuator shutdown endpoint:

```bash
curl -X POST http://localhost:8080/actuator/shutdown
```

### Configuration

Key settings in `src/main/resources/application.properties`:

| Property | Value |
|----------|-------|
| Server port | `8080` |
| Actuator endpoints | All exposed |

Datasource connection details are not configured here — `spring-boot-docker-compose` supplies them automatically from the running `compose.yaml` Postgres service.

## Example Usage

**Register a user:**
```bash
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "secret123"}'
```

**Create a quiz:**
```bash
curl -X POST http://localhost:8080/api/quizzes \
  -u user@example.com:secret123 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Kotlin basics",
    "questions": [
      {
        "text": "What is a data class?",
        "options": ["A mutable class", "An immutable class with generated equals/hashCode", "An abstract class", "An interface"],
        "answer": 1
      }
    ]
  }'
```

**Solve a quiz:**
```bash
curl -X POST http://localhost:8080/api/quizzes/{id}/solve \
  -u user@example.com:secret123 \
  -H "Content-Type: application/json" \
  -d '{"answers": [1]}'
```

**Get completion history:**
```bash
curl http://localhost:8080/api/quizzes/completed \
  -u user@example.com:secret123
```
