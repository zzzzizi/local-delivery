# Local Delivery — Stage 5 DTOs and validation complete

A local peer-to-peer delivery project for Oslo. The completed Python stages 1–4 have been migrated to Java 21, Spring Boot, and Maven: health checks, PostgreSQL models, demo users, validation, and create/list/detail APIs all work. These features correspond to the revised Java plan through Stage 7. Job acceptance (Stage 8) is next and has not been started.

Stage 5 is now complete, including `AcceptRequestDto` and `UpdateStatusRequest`. These DTOs validate input for the future acceptance and status-update workflows; those endpoints and business rules will be implemented in Stages 8 and 9.

The Next.js frontend remains the existing setup page. Request forms, job pages, authentication, payments, GPS, and chat belong to later stages.

## Stack and architecture

- Backend: Java 21, Spring Boot 3.5.16, Spring Web, Spring Data JPA/Hibernate, Jakarta Validation, Maven Wrapper 3.9.16.
- Database: PostgreSQL, managed migrations with Flyway, schema validation with Hibernate.
- API documentation: springdoc OpenAPI and Swagger UI.
- Tests: JUnit 5, Spring Boot Test, MockMvc, and actual PostgreSQL.
- Frontend: Next.js, React, TypeScript.

Controllers delegate to transactional services, services use repositories, and repositories persist JPA entities. Controllers return DTOs instead of entities. Dependencies use constructor injection.

```text
local-delivery/
├── .env.example
├── .gitignore
├── compose.yaml
├── README.md
├── backend/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/maven-wrapper.properties
│   └── src/
│       ├── main/
│       │   ├── java/com/localdelivery/
│       │   │   ├── LocalDeliveryApplication.java
│       │   │   ├── controller/  HealthController, DeliveryRequestController
│       │   │   ├── service/     DeliveryRequestService, DemoUserService
│       │   │   ├── repository/  UserRepository, DeliveryRequestRepository
│       │   │   ├── model/       User, DeliveryRequest, RequestCategory, DeliveryStatus
│       │   │   ├── dto/         CreateDeliveryRequestRequest, DeliveryRequestResponse,
│       │   │   │                AcceptRequestDto, UpdateStatusRequest,
│       │   │   │                OsloDeadlineDeserializer
│       │   │   ├── exception/   ResourceNotFoundException, ApiExceptionHandler
│       │   │   └── config/      DemoDataConfiguration, DatabaseMigrationConfiguration
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-dev.properties
│       │       └── db/migration/
│       │           ├── V1__initial_schema.sql
│       │           └── V2__java_ids_and_utc_timestamps.sql
│       └── test/java/com/localdelivery/
│           ├── DeliveryApiTest.java
│           ├── LegacyMigrationTest.java
│           ├── ApiErrorTest.java
│           ├── WorkflowDtoValidationTest.java
│           └── PostgresTestDatabase.java
└── frontend/  Existing Next.js application
```

## Prerequisites

- JDK 21, with `java` on PATH (`java -version`).
- PostgreSQL available locally. This checkout retains its existing Docker Compose database; Docker Desktop must be running to use it. An independently installed PostgreSQL database also works.
- Node.js 20.9+ and npm for the frontend.
- Free ports 8080 (backend), 3000 (frontend), and 5433 (this checkout's PostgreSQL).

No global Maven installation is needed. The wrapper downloads Maven and dependencies on first use. On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Start on this machine

The existing `.env` and PostgreSQL data have been preserved. Start the database from the project root, then start Java from `backend`:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery
docker compose up -d --wait db
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway applies migrations at startup, Hibernate validates the schema, and the `dev` profile creates the two demo users if needed. Seeding is repeatable and refuses to overwrite unrelated users occupying their IDs or emails.

| ID | Name | Email |
| --- | --- | --- |
| 1 | Customer Test | customer@example.com |
| 2 | Helper Test | helper@example.com |

Start the frontend in another terminal:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm run dev
```

Open [the frontend](http://localhost:3000), [API health](http://localhost:8080/api/health), and [Swagger UI](http://localhost:8080/docs).

If the servers are already running, use them directly. Stop a server with Ctrl+C before restarting. `docker compose stop db` stops this project's database while preserving its volume.

To build and run an executable JAR (stop any running JAR before rebuilding it):

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw package
java -jar target/local-delivery-0.1.0.jar --spring.profiles.active=dev
```

## First-time setup and environment variables

For a new checkout, copy `.env.example` to `.env` at the project root and fill in `DB_PASSWORD` before starting the database. Preserve the existing `.env` when upgrading. Run `npm ci` from `frontend` once, then follow the commands above using your own checkout path.

| Variable | Default / purpose |
| --- | --- |
| `DB_HOST` | `127.0.0.1` |
| `DB_PORT` | `5433`; use `5432` if your independently installed server uses it |
| `DB_NAME` | `local_delivery` |
| `DB_USERNAME` | `local_delivery` |
| `DB_PASSWORD` | Required; no password is hardcoded |
| `SERVER_PORT` | `8080` |
| `SEED_DEMO_USERS` | `false` outside the dev profile; dev enables seeding |

Spring loads the root `.env` when started from `backend`, treating it as Java properties. Use unquoted values. Exported environment variables override file values for the same key. The old `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` names remain supported so the existing installation needs no secret changes; prefer the new `DB_*` keys and avoid defining both families at once.

The retained `compose.yaml` supports either naming family and only runs PostgreSQL. Backend/frontend Docker images have not been added. Its database port is bound to localhost and data stays in the existing named volume. Changing a password in `.env` does not change credentials already stored in an initialized database.

For an external PostgreSQL installation, create the configured database and role yourself and omit the Docker commands. The role needs schema/table permissions; test runs also require permission to create and drop their own temporary schemas.

## API changes from Python

| Previous API | Java API |
| --- | --- |
| Port `8000` | Port `8080` |
| `GET /health` | `GET /api/health` |
| `POST /requests` | `POST /api/requests` |
| `GET /requests` | `GET /api/requests` |
| `GET /requests/{request_id}` | `GET /api/requests/{id}` |
| JSON `customer_id`, `helper_reward`, etc. | JSON `customerId`, `helperReward`, etc. |
| FastAPI error `detail` | Consistent `timestamp`, `status`, `error`, `message`, `fields` |

Old URLs and snake_case request bodies are not compatibility aliases. Refresh old Swagger tabs using the new [documentation URL](http://localhost:8080/docs). OpenAPI JSON is at `/api/openapi.json`.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/health` | 200, `{"status":"ok"}`; liveness rather than database readiness |
| `POST /api/requests` | 201, saved request with `status: "OPEN"` and `helperId: null` |
| `GET /api/requests` | 200, array of OPEN requests, newest first; descending ID breaks timestamp ties |
| `GET /api/requests/{id}` | 200, complete request regardless of status; 404 if missing |

Validation preserves the existing behavior:

- All creation fields below are required except `shoppingBudget`, which may be omitted or null.
- `customerId` must be a positive integer identifying an existing user; a missing customer returns 404.
- Category is one of PACKAGE, BUY, or PICKUP. Enum names are case-sensitive; numeric enum values are rejected.
- Title, description, and addresses must contain text. Surrounding whitespace is stripped. Titles allow 200 characters and addresses allow 500.
- Amounts are nonnegative with up to eight integer digits and two decimal places. Zero reward is allowed. Response amounts are JSON strings for decimal precision.
- Deadlines must be in the future. An explicit timezone offset is preferred; a timestamp without one is interpreted in Europe/Oslo. Ambiguous/nonexistent local times during daylight-saving changes require an explicit offset. Responses use UTC (`Z`).
- Unknown fields, including client-supplied IDs, helper assignments, and status, are rejected.

Invalid bodies or path IDs return 422. Database constraint conflicts return 409. Temporary database connection failures return 503 without exposing database exception details. Missing resources return 404. Failed service transactions roll back.

## Stage 5 DTOs and validation

All four planned request/response DTOs are present:

| DTO | Fields and validation |
| --- | --- |
| `CreateDeliveryRequestRequest` | Validates customer ID, category, required text, amounts, and deadline as described above |
| `DeliveryRequestResponse` | Complete response containing IDs, request details, amounts, status, and timestamps |
| `AcceptRequestDto` | `helperId`: required positive `Long`; example `{"helperId":2}` |
| `UpdateStatusRequest` | `status`: required `DeliveryStatus`; example `{"status":"PICKED_UP"}` |

The new DTOs use Java records and Jakarta validation:

```java
public record AcceptRequestDto(@NotNull @Positive Long helperId) {}

public record UpdateStatusRequest(@NotNull DeliveryStatus status) {}
```

`ApiExceptionHandler` already provides centralized validation errors. Missing or invalid constrained fields return 422 with field details. Malformed JSON, unknown enum names, numeric enums, incorrect JSON types, and extra fields return 422 with a safe error message. The new tests exercise these DTOs through isolated MockMvc controllers with the application's Jackson configuration and error handler.

The test controllers live only in test sources and are excluded from application component scanning. They are not available on the running server. Acceptance and status-update routes are not added by this stage, so their DTOs will appear in Swagger when those routes are implemented. Helper existence, self-acceptance, concurrency, and valid status transitions are service rules for Stages 8 and 9; Stage 5 only validates the input shape and values.

To verify this stage independently, run:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=WorkflowDtoValidationTest test
```

Expect 34 passing cases, including valid helper IDs, every known status, missing/null fields, nonpositive helper IDs, invalid types, overflow, unknown fields, malformed JSON, and invalid enum values. This focused MVC test does not need a database. The full suite also checks numeric category rejection and verifies that future workflow routes and test-only routes are absent from OpenAPI.

## Manual testing

1. Start PostgreSQL and the Java backend, then open [Swagger UI](http://localhost:8080/docs).
2. Expand `GET /api/health`, select **Try it out → Execute**, and expect 200 with `{"status":"ok"}`.
3. Expand `POST /api/requests`, select **Try it out**, and paste the following JSON. Change the deadline if testing after the example date.

```json
{
  "customerId": 1,
  "category": "BUY",
  "title": "Buy groceries",
  "description": "Please buy milk, eggs and bread.",
  "pickupAddress": "KIWI Majorstuen, Oslo",
  "deliveryAddress": "Frogner, Oslo",
  "shoppingBudget": 300,
  "helperReward": 80,
  "deadline": "2027-01-15T18:00:00+01:00"
}
```

4. Execute. Expect 201, a generated ID, OPEN status, and a null helper. Each successful submission creates a new row.
5. Execute `GET /api/requests`. The created request should appear first.
6. Execute `GET /api/requests/{id}` using the returned ID. Confirm the saved fields and UTC deadline.
7. Set `helperReward` to `-1` or remove `title`; creation must return 422 and save nothing. Use `customerId: 999999` to check the 404 error.
8. Fetch request ID `9223372036854775807`; expect 404. Use `abc` as the ID to check 422.

Read-only terminal checks:

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/requests
curl -i http://localhost:8080/api/requests/1
curl -i http://localhost:8080/api/requests/9223372036854775807
```

## Data model and migration

User and request IDs use Java `Long` and PostgreSQL `BIGINT`. Customer/helper relationships use separate `@ManyToOne` mappings to User, with helper nullable. PostgreSQL native enums retain all three categories and six statuses: OPEN, ACCEPTED, PICKED_UP, DELIVERING, DELIVERED, CANCELLED. Email uniqueness, rating bounds, foreign keys, nonnegative amounts, and existing indexes remain enforced by PostgreSQL.

Money is `BigDecimal` / `NUMERIC(10,2)`. Entities use UTC `LocalDateTime`; the JDBC configuration binds these values directly to avoid shifts caused by the computer's timezone. JSON DTOs expose timestamps with a UTC offset. Entity lifecycle callbacks initialize timestamps and update `updatedAt` on ORM changes; direct SQL updates must set it explicitly.

Flyway owns schema changes; `spring.jpa.hibernate.ddl-auto=validate` checks mappings without changing tables. For a fresh database, V1 creates the original schema and V2 widens IDs and normalizes timestamps. For this existing installation, the migration strategy recognizes exactly Alembic revision `0001`, baselines V1, and applies V2. Other legacy revisions and unknown nonempty schemas are rejected.

V2 preserves each timestamp's instant using `AT TIME ZONE 'UTC'` before storing it as a timestamp without timezone. The old `alembic_version` marker remains for provenance; Flyway now owns migration history in `flyway_schema_history`.

Before the live switch, backups were saved under the ignored `backups/` directory:

- `before-java-cutover.dump`: final PostgreSQL custom-format backup.
- `before-java-cutover-records.json`: complete record snapshot used for preservation checks.
- `python-backend-before-migration.tar.gz`: committed Python backend source from `3f4ae96`.

Both users and both existing requests were compared field by field after migration, including normalized timestamp instants. Do not run the old Python backend against the migrated database; recovering the old stack requires restoring its database backup into a separate database as well as restoring its source.

## Tests and verification

With PostgreSQL running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw test
```

The 116 tests cover health, creation in all categories, all Stage 5 DTOs and field validation, committed persistence from a separate connection, customer/helper relationships, timestamps and timezone handling, enum values, database constraints, rollback, idempotent seeding, listing only OPEN requests, deterministic order, details for all statuses, error responses, and populated legacy-schema migration.

Tests use unique `test_java_*` PostgreSQL schemas and drop only those schemas afterward. They do not clear development tables, substitute an in-memory database, or silently skip database failures.

With the frontend development server running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm test
```

The Java migration was verified on 2026-10-06 with 80 passing tests, the executable JAR running, migration V2 applied with existing records preserved, live health/list/detail/docs endpoints returning 200, and the frontend smoke test passing. The Python server was stopped and its implementation/configuration/tests were replaced by Java files.

Stage 5 completion adds 34 workflow DTO validation cases and two numeric-category regression cases. The full suite passes all 116 tests with no failures or skips. The updated executable JAR was built and restarted on port 8080; live health, docs, request listing, and 422 validation responses were verified without changing saved requests.

## Important implementation examples

The controller only validates input and delegates:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public DeliveryRequestResponse create(@Valid @RequestBody CreateDeliveryRequestRequest request) {
    return service.create(request);
}
```

The repository declares the available-jobs query:

```java
List<DeliveryRequest> findByStatusOrderByCreatedAtDescIdDesc(DeliveryStatus status);
```

`DeliveryRequestService.create` runs in a transaction, checks the customer, sets OPEN status, saves the entity, and maps it to `DeliveryRequestResponse`.

## Files changed and Git

This migration adds the Maven wrapper/build, Java packages, properties, Flyway scripts, and Java tests listed above. It removes the Python app, Python tests, requirements, and Alembic configuration/scripts. It updates `.env.example`, `.gitignore`, `compose.yaml`, and this README. Frontend source and existing credentials are unchanged.

Stage 5 completion adds `AcceptRequestDto.java`, `UpdateStatusRequest.java`, and `WorkflowDtoValidationTest.java`. It updates `application.properties` to reject numeric enums, extends `DeliveryApiTest.java` for regression coverage, and updates this README. No database migration is required.

`.env`, build outputs, dependency folders, IDE files, and database backups are ignored. No commit or push is made automatically. Review with `git status` and `git diff` from the project root.

Suggested commit:

```text
feat: add request DTOs and validation
```

Stop here. The next requested stage can add the Java Stage 8 acceptance workflow and concurrency protection.
