# HRMS Labour Attendance and Overtime API

## Project Overview

This project is a Java 17 Spring Boot backend for construction/labour workforce attendance. It tracks workers, work sites, clock-in/clock-out logs, overtime calculation, active workers in Redis, and monthly overtime settlement.

The goal is to provide a reliable API for daily workforce operations while handling the assignment tickets around CORS, Redis failure tolerance, N+1 queries, transaction-safe SMS notification, and Supabase connection pooling.

## Tech Stack

- Java 17
- Spring Boot 3.x
- Spring Web, Validation, Security, Actuator
- Hibernate/JPA
- PostgreSQL/Supabase
- Redis
- Maven
- Lombok
- springdoc-openapi Swagger UI

## Forked From

Forked from: [SpringBoot Employee Management System]

I chose that base because it has a clean JPA-oriented structure and PostgreSQL-friendly setup patterns that map well to this attendance and overtime domain.

## AI Tools Used

Claude (Anthropic) was used for code generation, architecture review, and debugging support. It helped with entity design, service logic, exception handling, and ticket-specific fixes.

Guardrails used while working with AI:

- Kept entities, repositories, and services aligned with the written requirements.
- Preferred Spring Boot and JPA conventions over invented abstractions.
- Added explicit ticket-driven checks for CORS, Redis degradation, JOIN FETCH, AFTER_COMMIT events, and Supabase pooler settings.
- Avoided adding fictional external API behavior for LF-205 because no external API call exists in this codebase.

Assignment note: the required hand-drawn sketch describing thought process and hallucination-control approach should be drawn manually with pen or pencil. Do not generate that sketch with AI.

## Setup Instructions

Prerequisites:

- Java 17+
- Maven
- Redis, local or cloud
- Supabase PostgreSQL project, or local PostgreSQL/H2 for quick development

```bash
# Clone the repo
git clone [your-repo-url]
cd hrms

# Set environment variables
export SUPABASE_DB_URL=jdbc:postgresql://[project].supabase.co:6543/postgres
export SUPABASE_DB_USERNAME=postgres
export SUPABASE_DB_PASSWORD=[your-password]
export REDIS_HOST=localhost
export REDIS_PORT=6379
export CORS_ALLOWED_ORIGINS=http://localhost:3000

# Run
mvn spring-boot:run

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

For staging, use `SPRING_PROFILES_ACTIVE=staging` and set `SUPABASE_POOLER_URL`.

## Java Backend Hosting

This repository includes a production Dockerfile and `render.yaml` blueprint for deploying the Spring Boot API as a Render web service.

Render setup:

1. Open Render and create a new Blueprint from this GitHub repo.
2. Render reads `render.yaml`, builds the Docker image, and starts the Spring Boot jar.
3. Add these environment variables when prompted:

```bash
SPRING_PROFILES_ACTIVE=staging
SUPABASE_POOLER_URL=jdbc:postgresql://[project].pooler.supabase.com:6543/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=[your-password]
REDIS_HOST=[your-redis-host]
REDIS_PORT=6379
REDIS_PASSWORD=[your-redis-password]
CORS_ALLOWED_ORIGINS=https://hrms-labour-attendance-api.vercel.app,http://localhost:3000
```

Health check:

```bash
curl https://[your-render-service].onrender.com/actuator/health
```

## Supabase Setup

1. Create a project at Supabase.
2. Open Project Settings > Database > Connection string.
3. Use the Connection Pooler URL in Transaction mode.
4. Use port `6543` through PgBouncer, not the direct database port `5432`.
5. Put the pooler JDBC URL in `SUPABASE_POOLER_URL` for the staging profile.

## Design Decisions

- Redis active workers use individual keys like `active:workers:{id}` for O(1) clock-out removal and per-entry TTL.
- Overtime hourly wage is derived from `dailyWageRate / 8`.
- The monthly overtime cap is checked at clock-out. Attendance is still recorded, while payable overtime is capped.
- Settlement is one transaction: all entries are marked `SETTLED` together or none are.
- SMS notification is published as an application event and handled with `@TransactionalEventListener(AFTER_COMMIT)`.
- Attendance log pagination uses `JOIN FETCH` for worker and site to avoid N+1 lazy-load queries.
- CORS is connected to the Spring Security filter chain so browser preflight requests are handled before authorization checks.
- Redis failures are logged and degraded gracefully for direct active-worker operations and cache operations.
- Staging uses Supabase PgBouncer/Hikari settings designed for short-lived pooled connections.

## API Examples

### Clock In

```bash
curl -X POST http://localhost:8080/api/attendance/clock-in \
  -H "Content-Type: application/json" \
  -d '{"workerId": 1, "siteId": 1}'
```

### Clock Out

```bash
curl -X POST http://localhost:8080/api/attendance/clock-out \
  -H "Content-Type: application/json" \
  -d '{"workerId": 1}'
```

### Active Workers from Redis

```bash
curl http://localhost:8080/api/attendance/active
```

### Attendance Log with Pagination

```bash
curl "http://localhost:8080/api/attendance/log?workerId=1&from=2026-01-01&to=2026-01-31&page=0&size=20"
```

### Overtime Summary

```bash
curl "http://localhost:8080/api/overtime/summary/1?month=2026-04"
```

### Settle Overtime

```bash
curl -X POST "http://localhost:8080/api/overtime/settle/1?month=2026-04"
```

## Error Format

All handled errors return structured JSON:

```json
{
  "error": "DUPLICATE_CLOCK_IN",
  "message": "Worker is already clocked in at Site: Downtown Tower",
  "timestamp": "2026-05-25T12:00:00Z"
}
```

## Verification Checklist

- App starts without Redis because Redis calls are lazy and direct active-worker calls are caught/logged.
- Duplicate clock-in returns `409` with `DUPLICATE_CLOCK_IN`.
- Clock-out without clock-in returns `400` with `NOT_CLOCKED_IN`.
- `GET /api/attendance/active` reads from Redis only.
- `GET /api/attendance/log` uses `JOIN FETCH` with pagination.
- Settlement is wrapped in `@Transactional`.
- SMS placeholder fires only after commit using `@TransactionalEventListener`.
- `application.yml` uses environment variables instead of hardcoded secrets.
- `application-staging.yml` contains Supabase PgBouncer/Hikari settings.
- Swagger UI is exposed at `/swagger-ui.html`.
