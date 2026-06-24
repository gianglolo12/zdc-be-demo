# zdc-be

ZingCard Distribution — Backend. **Spring Boot 3 · Java 21 · Maven · MySQL 8 · Redis 7.**

Source repo for the event-driven harness: PRDs + BE technical specs live in the control-plane (`zdc-control-plane`); the harness overlays them and implements features here per the `be/` agent bundle.

## Run
```bash
docker compose up -d        # MySQL :33306, Redis :36379
mvn spring-boot:run
# health: GET http://localhost:8080/api/v1/health
```

## Test
```bash
mvn test                    # H2 in-memory, no external services needed
```

## Layout (rule-layering)
`controller` → `service` → `repository`. DTOs only at the web edge. Flyway migrations in `src/main/resources/db/migration`. Errors as RFC-7807. All endpoints under `/api/v1`.
