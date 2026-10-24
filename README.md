# Local Delivery — Stage 18 complete

A local peer-to-peer delivery project for Oslo, using Java 21, Spring Boot, Maven, and Next.js. Stages 1–18 are complete, including the customer/helper workflow, basic UI improvements, and full-workflow integration testing. The application is working and tested against PostgreSQL, over real HTTP, and in the browser. Stage 19 has not been started.

Stage 5 is complete, including `AcceptRequestDto` and `UpdateStatusRequest`. Acceptance uses `AcceptRequestDto`; status updates now use `UpdateStatusRequest`.

Stage 6 has been reviewed against every requirement. The existing create-request endpoint is complete, and its response now uses the exact decimal scale and timestamp precision stored in PostgreSQL.

Stage 7 is also reviewed and complete. Listing returns OPEN requests newest first, details return the complete request in any status, and missing requests return 404. Expanded integration tests verify mixed datasets, complete response fields, and that reads do not change stored records.

Stage 8 adds transactional acceptance with helper validation and optimistic locking. Only one helper can win a concurrent acceptance attempt; duplicate or competing attempts return 409.

Stage 9 adds the status PATCH endpoint and centralized transitions through pickup, delivery, and completion. Cancellation is allowed before pickup. Invalid transitions and competing updates return 409 without overwriting saved state.

Stage 10 adds an explicit already-accepted exception and a consistent JSON error contract for business errors, validation, Spring MVC request errors, and unexpected server failures. Protocol headers and field validation details are preserved; internal exception details stay out of responses.

Stage 11 provides Home, Create Request, Available Jobs, My Requests, and My Deliveries routes with shared navigation, active-link highlighting, and page titles. Create Request, Available Jobs, and My Requests are now implemented. My Deliveries now runs the completed Stage 16 helper workflow. Authentication, payments, GPS, and chat also remain out of scope.

Stage 12 adds the eight-field Create Request form, posts as customer 1, prevents duplicate submissions while pending, shows client/backend validation and server errors, and displays the saved request after success. Deadline entry and display use Oslo time.

Stage 13 fetches OPEN requests and displays linked cards with category, title, addresses, reward, Oslo deadline, and status. Loading, empty results, API errors, and refresh/retry are handled. Card destinations now open the Stage 14 detail page.

Stage 14 displays complete request details and accepts OPEN jobs as helper 2. It refreshes after acceptance, handles already-accepted conflicts, prevents duplicate submissions while pending, and provides loading, retry, not-found, and manual-refresh states.

Stage 15 adds `GET /api/users/{userId}/requests` and a working My Requests page for customer 1. It displays requests in every status, newest first, with category, title, status, assigned helper, and Oslo deadline. Refresh, loading, empty results, API errors, and retry are handled.

Stage 16 adds helper delivery listing and the three delivery-status actions. All 276 backend tests and 126 frontend tests pass, along with lint, TypeScript, and the production build. Browser checks verify the full delivery workflow, conflicts, duplicate-click prevention, refresh recovery, and mobile layout; see Stage 16 below.

Stage 17 improves navigation, responsive cards, status badges, form grouping, loading placeholders, empty states, and error feedback using the existing CSS and React components. All automated checks pass, with browser verification at desktop, tablet, and phone widths.

Stage 18 adds four real-HTTP integration cases and verifies the complete BUY journey in the browser, from creating a request to the customer seeing Delivered. All 280 backend tests and 126 frontend tests pass.

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
│       │   │   ├── controller/  HealthController, DeliveryRequestController,
│       │   │   │                UserRequestController, UserDeliveryController
│       │   │   ├── service/     DeliveryRequestService, DemoUserService
│       │   │   ├── repository/  UserRepository, DeliveryRequestRepository
│       │   │   ├── model/       User, DeliveryRequest, RequestCategory, DeliveryStatus
│       │   │   ├── dto/         CreateDeliveryRequestRequest, DeliveryRequestResponse,
│       │   │   │                AcceptRequestDto, UpdateStatusRequest,
│       │   │   │                OsloDeadlineDeserializer
│       │   │   ├── exception/   ResourceNotFoundException, RequestNotOpenException,
│       │   │   │                SelfAcceptanceException, InvalidStatusTransitionException,
│       │   │   │                RequestAlreadyAcceptedException,
│       │   │   │                ApiExceptionHandler
│       │   │   └── config/      DemoDataConfiguration, DatabaseMigrationConfiguration
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-dev.properties
│       │       └── db/migration/
│       │           ├── V1__initial_schema.sql
│       │           ├── V2__java_ids_and_utc_timestamps.sql
│       │           └── V3__request_optimistic_locking.sql
│       └── test/java/com/localdelivery/
│           ├── DeliveryApiTest.java
│           ├── CustomerRequestsApiTest.java
│           ├── HelperDeliveriesApiTest.java
│           ├── AcceptRequestApiTest.java
│           ├── UpdateStatusApiTest.java
│           ├── LegacyMigrationTest.java
│           ├── ApiErrorTest.java
│           ├── ApiErrorContractTest.java
│           ├── WorkflowDtoValidationTest.java
│           └── PostgresTestDatabase.java
└── frontend/
    ├── .env.example
    ├── next.config.mjs
    ├── package.json / package-lock.json
    ├── src/
    │   ├── app/
    │   │   ├── layout.tsx / globals.css / page.tsx
    │   │   ├── requests/new/page.tsx
    │   │   ├── requests/[id]/page.tsx / not-found.tsx
    │   │   ├── jobs/page.tsx
    │   │   ├── my-requests/page.tsx
    │   │   └── my-deliveries/page.tsx
    │   ├── components/  navigation.tsx, page-shell.tsx, create-request-form.tsx,
    │   │                available-jobs.tsx, refresh-jobs-button.tsx, request-details.tsx,
    │   │                customer-requests.tsx, refresh-requests-button.tsx, delivery-board.tsx
    │   └── lib/         api.ts, api-config.mjs, create-request.ts, available-jobs.ts,
    │                    request-details.ts, customer-requests.ts, helper-deliveries.ts
    └── tests/           api.test.mjs, create-request.test.mjs, available-jobs.test.mjs,
                         request-details.test.mjs, customer-requests.test.mjs,
                         helper-deliveries.test.mjs, home.test.mjs
```

## Prerequisites

- JDK 21, with `java` on PATH (`java -version`).
- PostgreSQL available locally. This checkout retains its existing Docker Compose database; Docker Desktop must be running to use it. An independently installed PostgreSQL database also works.
- Node.js 22.17+ and npm for the frontend. Tests use Node's built-in TypeScript stripping to test the actual API helper without adding a test-runner dependency.
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

For a fresh checkout, first copy `frontend/.env.example` to `frontend/.env.local`. This machine already has that local file. Preserve it if it exists.

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm run dev
```

Open [the frontend](http://127.0.0.1:3000), [API health](http://127.0.0.1:8080/api/health), and [Swagger UI](http://127.0.0.1:8080/docs).

The backend binds to IPv4 `127.0.0.1`. Use that address explicitly: on this machine an unrelated Docker service answers IPv6 `localhost:8080`, which can send a browser or terminal request to the wrong application.

If the servers are already running, use them directly. Stop a server with Ctrl+C before restarting. `docker compose stop db` stops this project's database while preserving its volume.

To build and run an executable JAR (stop any running JAR before rebuilding it):

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw package
java -jar target/local-delivery-0.1.0.jar --spring.profiles.active=dev
```

## First-time setup and environment variables

For a new checkout, copy `.env.example` to `.env` at the project root and fill in `DB_PASSWORD` before starting the database. Preserve the existing `.env` when upgrading. Run `npm ci` from `frontend` once and copy its `.env.example` to `.env.local`, then follow the commands above using your own checkout path.

| Variable | Default / purpose |
| --- | --- |
| `DB_HOST` | `127.0.0.1` |
| `DB_PORT` | `5433`; use `5432` if your independently installed server uses it |
| `DB_NAME` | `local_delivery` |
| `DB_USERNAME` | `local_delivery` |
| `DB_PASSWORD` | Required; no password is hardcoded |
| `SERVER_PORT` | `8080` |
| `SEED_DEMO_USERS` | `false` outside the dev profile; dev enables seeding |
| `NEXT_PUBLIC_API_URL` | Set in `frontend/.env.local`; example `http://127.0.0.1:8080/api`, including `/api` |

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

Old URLs and snake_case request bodies are not compatibility aliases. Refresh old Swagger tabs using the new [documentation URL](http://127.0.0.1:8080/docs). OpenAPI JSON is at `/api/openapi.json`.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/health` | 200, `{"status":"ok"}`; liveness rather than database readiness |
| `POST /api/requests` | 201, saved request with `status: "OPEN"` and `helperId: null` |
| `GET /api/requests` | 200, array of OPEN requests, newest first; descending ID breaks timestamp ties |
| `GET /api/requests/{id}` | 200, complete request regardless of status; 404 if missing |
| `GET /api/users/{userId}/requests` | 200, all requests created by that customer, newest first; empty array if none; 404 if customer is missing |
| `GET /api/users/{userId}/deliveries` | All jobs assigned to that helper, newest first; empty array if none; 404 if helper is missing |
| `POST /api/requests/{id}/accept` | 200, complete accepted request with the chosen helper; 409 if no longer available |
| `PATCH /api/requests/{id}/status` | 200, complete updated request; 409 for invalid transitions or concurrent changes |

Validation preserves the existing behavior:

- All creation fields below are required except `shoppingBudget`, which may be omitted or null.
- `customerId` must be a positive integer identifying an existing user; a missing customer returns 404.
- Category is one of PACKAGE, BUY, or PICKUP. Enum names are case-sensitive; numeric enum values are rejected.
- Title, description, and addresses must contain text. Surrounding whitespace is stripped. Titles allow 200 characters and addresses allow 500.
- Amounts are nonnegative with up to eight integer digits and two decimal places. Zero reward is allowed. Response amounts are JSON strings for decimal precision.
- Deadlines must be in the future. An explicit timezone offset is preferred; a timestamp without one is interpreted in Europe/Oslo. Ambiguous/nonexistent local times during daylight-saving changes require an explicit offset. Responses use UTC (`Z`).
- Unknown fields are rejected. Creation rejects client-supplied IDs, helper assignments, and status. Acceptance accepts only `helperId`; status updates accept only `status`.

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

The test controllers live only in test sources and are excluded from application component scanning. They are not available on the running server. Stage 5 only validates input shape and values. Stage 8 exposes the acceptance DTO in Swagger and enforces helper existence, self-acceptance, and concurrency rules. Stage 9 exposes the status-update DTO and enforces transition rules.

To verify this stage independently, run:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=WorkflowDtoValidationTest test
```

Expect 34 passing cases, including valid helper IDs, every known status, missing/null fields, nonpositive helper IDs, invalid types, overflow, unknown fields, malformed JSON, and invalid enum values. This focused MVC test does not need a database. The full suite also checks numeric category rejection and verifies that implemented routes appear in OpenAPI while test-only routes remain absent.

## Stage 6 creation API verification

`POST /api/requests` uses the controller → transactional service → repository flow, validates the DTO, looks up the customer, creates an OPEN request with a null helper, saves timestamps, and returns HTTP 201 with the saved record.

| Required case | Verified behavior |
| --- | --- |
| Successful creation | 201 for PACKAGE, BUY, and PICKUP; generated ID, OPEN status, null helper, and stored timestamps |
| Customer not found | 404; no request saved |
| Missing required title | 422 with a `title` field error; no request saved |
| Invalid category | 422; no request saved |
| Negative reward | 422 with a `helperReward` field error; no request saved |
| Negative shopping budget | 422 with a `shoppingBudget` field error; no request saved |

The review found that POST could return values with different precision from the saved row: for example, `"80.2"` instead of `"80.20"`, or a nanosecond deadline before PostgreSQL rounded it to microseconds. Creation now flushes and refreshes the entity in the same transaction before mapping the response. Three regression cases compare the entire POST and GET responses, then independently verify the monetary and timestamp values through JDBC.

Run the API checks with PostgreSQL running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=DeliveryApiTest test
```

The original API/model suite has 82 passing cases, including the Stage 7 checks below. To run only the three precision regression cases, use `./mvnw '-Dtest=DeliveryApiTest#creationResponseMatchesCommittedRecord' test`. The full backend suite now contains 280 cases, including acceptance, status-update, error-contract, customer-tracking, helper-delivery, and complete real-HTTP workflow suites.

## Stage 7 listing and detail verification

| Required behavior | Verified result |
| --- | --- |
| `GET /api/requests` lists OPEN requests | 200 with complete DTOs for all categories and customers; newest creation time first, then highest ID |
| Non-OPEN requests are excluded | ACCEPTED, PICKED_UP, DELIVERING, DELIVERED, and CANCELLED records are excluded, including when they are newer than OPEN records |
| `GET /api/requests/{id}` retrieves one request | 200 with every field, including helper ID, exact monetary values, and timestamps, for all six statuses |
| Request not found | 404 with the consistent API error response |

An empty available-jobs list returns `[]`. Invalid or nonpositive IDs return 422. Both endpoints use read-only service transactions and return DTOs; database snapshots in the integration tests confirm that reads leave records unchanged. Database-connection failures return safe 503 errors for both endpoints. Filtering and pagination remain optional future additions.

The existing application code satisfied the requirements. This review strengthens tests without changing the running API: a mixed dataset includes all three OPEN categories, two customers, and every non-OPEN status; the per-status tests compare every response field; both read paths are checked for database failure handling.

With PostgreSQL running, verify the API and error cases:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=DeliveryApiTest,ApiErrorTest test
```

Manual Stage 7 checks require no data changes:

1. Open [the available requests](http://127.0.0.1:8080/api/requests). Expect HTTP 200, OPEN statuses only, and descending creation time/ID.
2. Open [request 4](http://127.0.0.1:8080/api/requests/4), the Stage 6 verification record. Expect HTTP 200 and the same complete fields as its list entry. On a fresh database, use the ID of a request you created.
3. Open [a missing request](http://127.0.0.1:8080/api/requests/9223372036854775807). Expect HTTP 404 and `Delivery request not found.`
4. Open [an invalid request ID](http://127.0.0.1:8080/api/requests/abc). Expect HTTP 422.
5. In [Swagger UI](http://127.0.0.1:8080/docs), expand either GET request operation and select **Try it out → Execute**. The list needs no parameters; details require an existing ID.

Mixed-status filtering and empty-list behavior are verified in isolated test schemas; no development request needs to be edited to test them.

## Stage 8 — accept a request

`POST /api/requests/{id}/accept` accepts this body:

```json
{"helperId": 2}
```

The controller validates the ID and DTO and delegates to `DeliveryRequestService.accept`. Within one transaction, the service loads the request, checks that it is OPEN and unassigned, verifies the helper exists and differs from the customer, assigns that helper, and changes the status to ACCEPTED. It saves the update, refreshes database values, and returns the complete request. `createdAt` remains unchanged, `updatedAt` advances, and the accepted request disappears from the default OPEN listing.

| Case | Response |
| --- | --- |
| Successful acceptance | 200; assigned `helperId`, status ACCEPTED |
| Missing request or helper | 404 with an explanatory message |
| Customer accepts their own request | 400; request unchanged |
| Request is already accepted or has any other non-OPEN status | 409; original assignment unchanged |
| A competing transaction wins first | 409; original assignment unchanged |
| Invalid body or request ID | 422; request unchanged |

Repeated acceptance is rejected even for the same helper. A legacy OPEN record that already has a helper is also rejected so acceptance cannot overwrite an assignment. Availability is checked before helper lookup; an unavailable request returns 409 even if the supplied helper is missing.

Concurrency protection uses `@Version` on `DeliveryRequest`. Flyway V3 adds `version BIGINT NOT NULL DEFAULT 0` for existing rows. Hibernate checks and increments the version when updating a request; a stale competing update rolls back and the error handler returns 409. The version is internal and is not exposed in response DTOs or accepted from clients. See [Hibernate's optimistic locking documentation](https://docs.hibernate.org/orm/6.6/userguide/html_single/#locking-optimistic).

The tests include a deterministic race: two PostgreSQL transactions load the same OPEN version before either updates it, then exactly one commits. A separate pair of simultaneous MockMvc requests returns one 200 and one 409. Other cases cover every category, every non-OPEN status, missing users/requests, self-acceptance, repeated acceptance, invalid bodies/IDs, timestamps, persisted assignment, and removal from OPEN jobs.

Run the 34 acceptance cases with PostgreSQL running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=AcceptRequestApiTest test
```

Manual checks on the running app:

1. Refresh [Swagger UI](http://127.0.0.1:8080/docs). Create a new request using customer 1 and the sample in the next section. Note its returned ID.
2. Expand **POST /api/requests/{id}/accept**, select **Try it out**, enter that ID, and submit `{"helperId":1}`. Expect 400; the customer cannot accept their own request.
3. Submit `{"helperId":9223372036854775807}`. Expect 404 for the missing helper; the request remains OPEN.
4. Submit `{"helperId":2}`. Expect 200, `helperId: 2`, and `status: "ACCEPTED"`.
5. Fetch the request by ID. Its complete response should match the acceptance response. Fetch the OPEN list; the accepted request should be absent.
6. Submit `{"helperId":2}` again. Expect 409, with the original helper and timestamps preserved.
7. To check invalid input, submit `{}` or `{"helperId":0}` and expect 422. Use a missing request ID to check 404.

For terminal testing, replace `REQUEST_ID` with the newly created ID:

```bash
curl -i -X POST http://127.0.0.1:8080/api/requests/REQUEST_ID/accept \
  -H 'Content-Type: application/json' \
  -d '{"helperId":2}'
```

The live verification created and accepted request **6**, titled **Stage 8 verification — acceptance**. It remains available at [request 6](http://127.0.0.1:8080/api/requests/6). Use a newly created OPEN request to repeat the successful acceptance step.

## Stage 9 — delivery status updates

`PATCH /api/requests/{id}/status` accepts only a status:

```json
{"status": "PICKED_UP"}
```

The domain enum `DeliveryStatus.canTransitionTo` defines the entire transition graph, shared by acceptance and status updates. The controller validates the ID and `UpdateStatusRequest`, then delegates to the transactional service. The service checks the transition, saves with optimistic locking, refreshes stored values, and returns the complete DTO with HTTP 200.

| Current status | Allowed next status | Endpoint |
| --- | --- | --- |
| OPEN | ACCEPTED | `POST /api/requests/{id}/accept`, with a valid helper |
| OPEN | CANCELLED | `PATCH /api/requests/{id}/status` |
| ACCEPTED | PICKED_UP or CANCELLED | `PATCH /api/requests/{id}/status` |
| PICKED_UP | DELIVERING | `PATCH /api/requests/{id}/status` |
| DELIVERING | DELIVERED | `PATCH /api/requests/{id}/status` |
| DELIVERED or CANCELLED | None | Terminal states |

Cancellation is permitted only before pickup. Cancelling an accepted request retains its helper assignment for history; cancelling an OPEN request leaves its helper null. Status PATCH cannot accept an OPEN request: it returns 409 directing the caller to the acceptance endpoint so helper validation and assignment cannot be bypassed. Legacy records without an assigned helper cannot advance through delivery statuses. Authentication and caller authorization remain scheduled for Stage 20.

Repeated statuses, skipped steps, backward transitions, cancellation after pickup, and any update to a terminal request return 409. Missing requests return 404; missing/null/unknown statuses, malformed bodies, extra fields, and invalid IDs return 422. Failed updates leave every saved field, timestamp, and version unchanged.

A successful update changes only `status`, `updatedAt`, and the internal optimistic-lock version. Customer, helper, details, amounts, deadline, and `createdAt` are preserved. The response exactly matches a subsequent GET. No new database migration is needed; the existing V3 `version` column protects status updates and acceptance in the same way.

Run the 65 status cases with PostgreSQL running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=UpdateStatusApiTest test
```

These cover all 36 status pairs, the complete creation/acceptance/delivery flow for all categories, invalid input, missing requests, unassigned legacy records, and concurrent updates. Two deterministic races load the same PostgreSQL row version before either write: cancellation versus acceptance, and cancellation versus pickup. Exactly one transaction commits. Simultaneous HTTP pickup calls likewise return one 200 and one 409.

Manual testing in [Swagger UI](http://127.0.0.1:8080/docs):

1. Refresh the page. Create a **new** request with customer 1 using the JSON below; note the returned ID.
2. PATCH that ID with `{"status":"ACCEPTED"}`. Expect 409 instructing you to use the acceptance endpoint; GET still shows OPEN and a null helper.
3. POST `/api/requests/{id}/accept` with `{"helperId":2}`. Expect 200 and ACCEPTED.
4. PATCH with `{"status":"DELIVERED"}`. Expect 409; GET still shows ACCEPTED.
5. PATCH sequentially with `{"status":"PICKED_UP"}`, `{"status":"DELIVERING"}`, and `{"status":"DELIVERED"}`. Each returns 200 and the requested status. GET after each step must match the PATCH response; helper 2 and the original `createdAt` remain unchanged.
6. PATCH the delivered request with `{"status":"DELIVERING"}` or `{"status":"CANCELLED"}`. Both return 409 and leave it DELIVERED. It remains absent from the OPEN listing.
7. PATCH with `{}`, `{"status":"UNKNOWN"}`, or `{"status":1}`. Expect 422. PATCH a missing ID such as `9223372036854775807` with `{"status":"PICKED_UP"}` and expect 404.
8. To test cancellation, create another new request and PATCH it with `{"status":"CANCELLED"}`. Expect 200, CANCELLED, and a null helper. Further updates return 409. Repeat with another request after accepting it to confirm helper 2 is retained on cancellation.

Terminal equivalent for each valid status change (replace `REQUEST_ID` with the new accepted request's ID, then change the status in order):

```bash
curl -i -X PATCH http://127.0.0.1:8080/api/requests/REQUEST_ID/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"PICKED_UP"}'
```

Live verification samples are available as [request 8: DELIVERED](http://127.0.0.1:8080/api/requests/8), [request 9: cancelled before acceptance](http://127.0.0.1:8080/api/requests/9), and [request 10: cancelled before pickup](http://127.0.0.1:8080/api/requests/10). Create a new request to repeat the successful transitions; these samples are terminal.

## Stage 10 — centralized API errors

`ApiExceptionHandler` uses `@RestControllerAdvice` and extends Spring's `ResponseEntityExceptionHandler`. Business exceptions and Spring MVC failures return the same JSON structure with `Content-Type: application/json`:

```json
{
  "timestamp": "2026-10-19T06:38:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Delivery request has already been accepted.",
  "fields": {}
}
```

`timestamp` is the current UTC time, `status` matches the HTTP response code, `error` is the HTTP reason phrase, and `message` explains the error. `fields` contains individual DTO validation messages, or `{}` when there are no field-specific messages. Responses exclude stack traces, exception class names, database details, and raw parser diagnostics.

| Failure | HTTP status | Behavior |
| --- | --- | --- |
| `ResourceNotFoundException` | 404 | Explains whether the request, customer, or helper is missing |
| `InvalidStatusTransitionException` | 409 | Explains the rejected transition |
| `RequestAlreadyAcceptedException` | 409 | `Delivery request has already been accepted.` |
| `RequestNotOpenException` | 409 | Explains that an unassigned non-OPEN request is unavailable |
| Self-acceptance | 400 | Customer cannot accept their own request |
| DTO validation | 422 | `Validation failed.` plus field messages |
| Malformed JSON, unknown enum/field, or invalid path parameter | 422 | Safe generic input message |
| Unknown endpoint | 404 | `Endpoint not found.` |
| Unsupported HTTP method | 405 | JSON error and the original `Allow` header |
| Unsupported response format | 406 | JSON error explaining the unsupported format |
| Unsupported request content type | 415 | JSON error and the original supported-media `Accept` header |
| Optimistic locking or database constraint conflict | 409 | Safe conflict message |
| Database connection/transaction creation unavailable | 503 | Safe message asking the caller to retry later |
| Unexpected application/serialization failure | 500 | Generic response; diagnostic exception logged on the server |

The acceptance service raises `RequestAlreadyAcceptedException` when a helper is already assigned or the request is already ACCEPTED. It retains `RequestNotOpenException` for other unavailable, unassigned requests, such as a request cancelled before acceptance. Both checks happen before helper lookup and leave saved data unchanged. Concurrent stale writes retain their existing 409 behavior.

The new handler branch is deliberately small:

```java
@ExceptionHandler(RequestAlreadyAcceptedException.class)
ResponseEntity<ApiError> alreadyAccepted(RequestAlreadyAcceptedException error) {
    return response(HttpStatus.CONFLICT, error.getMessage(), Map.of());
}
```

The shared response builder supplies the timestamp, status, reason phrase, message, and fields. Framework handling keeps the appropriate HTTP status and headers while replacing framework-specific bodies with the same safe shape. Existing 400/404/409/422/503 business behavior is preserved.

Run the 30 new contract cases and the existing error tests without PostgreSQL:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw -Dtest=ApiErrorContractTest,ApiErrorTest test
```

The contract suite covers all handler branches used by current endpoints, exact response fields, matching statuses/reason phrases, parseable UTC timestamps, field-validation messages, protocol headers, and suppression of internal diagnostics. The real PostgreSQL acceptance suite additionally checks the actual exception raised and confirms failed acceptance leaves the entire stored row unchanged. The full suite still runs with `./mvnw test` and PostgreSQL available.

Manual checks on the current development data (all are expected to fail without changing records):

1. Refresh [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html#/).
2. GET `/api/requests/9223372036854775807`: expect 404 and `Delivery request not found.`
3. POST `/api/requests` with `{}`: expect 422, `Validation failed.`, and all eight required field names in `fields`.
4. POST `/api/requests/6/accept` with `{"helperId":2}`: request 6 is already accepted; expect 409 and `Delivery request has already been accepted.`
5. PATCH `/api/requests/8/status` with `{"status":"PICKED_UP"}`: request 8 is DELIVERED; expect 409 explaining the invalid transition. Use `{"status":"UNKNOWN"}` to check the safe 422 input error.
6. Run the terminal checks below to check unknown routes and unsupported methods/content types. Every response must have the five JSON fields shown above.

```bash
curl -i http://127.0.0.1:8080/api/missing-endpoint
curl -i -X DELETE http://127.0.0.1:8080/api/requests/1
curl -i -X POST http://127.0.0.1:8080/api/requests/1/accept \
  -H 'Content-Type: text/plain' -d '{"helperId":2}'
curl -i -H 'Accept: application/xml' http://127.0.0.1:8080/api/requests
```

Expect 404, 405, 415, and 406 respectively. The 405 response includes `Allow`; the 415 response includes `Accept`. On a fresh database, create and accept a request for step 4, and complete delivery for step 5 using the preceding stages. The 500 and 503 branches are verified with injected failures in isolated tests; no failure-injection endpoint is added to the running application.

## Stage 11 — frontend navigation and API setup

The application now has five routes:

| Page | Route | Current behavior |
| --- | --- | --- |
| Home | `/` | Introduction and links to create a request or view jobs |
| Create Request | `/requests/new` | Working form and saved confirmation (Stage 12) |
| Available Jobs | `/jobs` | Live OPEN request cards, refresh/retry, empty/error/loading states (Stage 13) |
| Request Details | `/requests/{id}` | Complete details, accept as helper 2, conflicts, refresh/retry (Stage 14) |
| My Requests | `/my-requests` | Customer 1’s requests in every status, helper, deadline, refresh/retry (Stage 15) |
| My Deliveries | `/my-deliveries` | Helper 2’s assigned jobs and next status actions |

All pages share the root layout, header, navigation, and footer. Navigation uses Next.js `Link` and `usePathname`, with `aria-current="page"` on the active item. Links wrap on narrow screens; keyboard users can skip directly to the main content. Each feature page has its own document title. Stage 11 introduced page shells; Stages 12 and 13 implement Create Request and Available Jobs. Stage 15 implements My Requests; Stage 16 implements My Deliveries and its verified status workflow.

Configure the backend once in `frontend/.env.local`:

```dotenv
NEXT_PUBLIC_API_URL=http://127.0.0.1:8080/api
```

This is public configuration, so never put secrets in `NEXT_PUBLIC_*` variables. `api-config.mjs` validates the URL and removes trailing slashes. Missing or invalid configuration produces an explicit setup error. Restart the development server after changing it; rebuild the production frontend before using a different backend URL.

`src/lib/api.ts` exports the generic `apiRequest<T>()` helper and `ApiError`. Callers pass paths relative to the configured API root:

```ts
import { apiRequest, ApiError } from "./lib/api"; // Adjust the import to your file.

const health = await apiRequest<{ status: string }>("/health");

try {
  await apiRequest("/requests/9223372036854775807");
} catch (error) {
  if (error instanceof ApiError) {
    console.log(error.status, error.message, error.fields);
  }
}
```

The helper serializes JSON bodies, sets JSON headers, disables caching by default, uses a ten-second timeout unless the caller supplies an abort signal, and returns parsed JSON. Empty successful responses return `undefined`. Failed HTTP responses throw `ApiError`, preserving the backend message, HTTP status, and field-validation messages. Non-JSON failures receive a safe fallback message. Transport failures and cancellation propagate to the caller.

Server calls use the configured backend URL directly. Browser calls use the same-origin `/backend-api` prefix; `next.config.mjs` rewrites that prefix to the configured backend `/api` root. This lets client forms reuse the helper without adding CORS permissions to the Java backend. Backend URLs are kept out of page components. The proxy forwards methods and request bodies, including the backend's existing error responses.

For example, the shared request code chooses its base in one place:

```ts
const baseUrl = typeof window === "undefined" ? getApiBaseUrl() : API_PROXY_PATH;
```

Verify the frontend from its directory:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm run lint
npm run typecheck
npm run test:unit
npm run build
```

To test the production build, stop any running frontend, run `npm run start`, and run `npm test` in another terminal with the Java backend also running. Use `npm run dev` when editing the frontend; do not run both servers on port 3000 at once. `npm test` includes 24 isolated API/configuration cases, 21 request-form payload/validation/timezone cases, seven jobs-response contract cases, and nine live checks covering all five pages, navigation, active links, page titles, direct backend access, proxy health, validation error forwarding, and job cards matching the live API. `FRONTEND_TEST_URL` can override the test frontend address. Unit tests do not require either server.

Manual testing:

1. Open [Home](http://127.0.0.1:3000/). Click each of the five navigation links. Confirm its heading, document title, and active underline change.
2. Refresh each route directly. All should load successfully with the same navigation. Create Request should show its form, Available Jobs should load OPEN request cards, My Requests should load customer 1’s requests in all statuses, and My Deliveries should load helper 2’s assigned jobs and status controls.
3. On Home, click **Create a request** and **View available jobs**; confirm they reach `/requests/new` and `/jobs` respectively. Use the **Home** navigation link to return.
4. Use Tab to focus **Skip to content**, then Enter. Focus should move to the main content. At a narrow mobile width, confirm navigation wraps and buttons remain usable without horizontal scrolling.
5. Open [proxied health](http://127.0.0.1:3000/backend-api/health). Expect `{"status":"ok"}` from the Java backend.
6. Run the following command. Expect HTTP 422 with `Validation failed.` and field errors; no request is created:

```bash
curl -i -X POST http://127.0.0.1:3000/backend-api/requests \
  -H 'Content-Type: application/json' -d '{}'
```

## Stage 12 — create a delivery request

Open [Create Request](http://127.0.0.1:3000/requests/new). The page is a Server Component for metadata and introductory content; `CreateRequestForm` is a Client Component for input, validation, and submission. It reuses the existing JSON helper and same-origin proxy to call `POST /api/requests`.

| Field | Behavior |
| --- | --- |
| Category | Required: Package delivery (`PACKAGE`), Shopping (`BUY`), or Pickup (`PICKUP`) |
| Title | Required; trimmed; maximum 200 characters |
| Description | Required; trimmed; supports multiple lines |
| Pickup address | Required; trimmed; maximum 500 characters |
| Delivery address | Required; trimmed; maximum 500 characters |
| Shopping budget | Optional NOK amount; blank becomes JSON `null` |
| Helper reward | Required NOK amount; zero is allowed |
| Deadline | Required date and time, interpreted in `Europe/Oslo` |

Amounts accept 0 through 99,999,999.99, with up to two decimal places and a dot as the decimal separator. They are sent as JSON numbers, matching the Java DTO; the confirmation displays the backend's exact two-decimal strings. `customerId` is fixed to `1` for this MVP, with no editable identity, helper, or status fields. Authentication remains a later stage.

Client validation catches empty/whitespace-only text, invalid categories, length limits, and invalid money. Java remains authoritative for validation, including whether a deadline is in the future and whether an Oslo time is valid around daylight-saving changes. The form sends an offset-free local deadline so it is not accidentally interpreted in the browser's timezone. Saved UTC deadlines are displayed with `Intl.DateTimeFormat` explicitly set to `Europe/Oslo`.

While submitting, the fieldset and submit button are disabled, the button reads **Posting request…**, and a status message announces progress. A synchronous ref also blocks repeated submission events. Backend field errors appear next to their controls with `aria-invalid` and `aria-describedby`; the summary receives focus. Other HTTP errors show the backend message. Connection/timeout failures keep the entries and explain that submission could not be confirmed, without automatically retrying a potentially saved request.

After success, the form is replaced by a confirmation using the returned request: ID, category, title, description, addresses, budget, reward, deadline, status, and helper availability. **Create another request** clears the form and focuses Category. This stage does not implement the later request-details route or job listing.

The submission uses the existing API helper:

```tsx
const result = prepareRequest(values);
if (result.errors) {
  setErrors(result.errors);
  setMessage("Please correct the highlighted fields.");
  return;
}
const request = await apiRequest<DeliveryRequest>("/requests", {
  method: "POST",
  body: result.payload, // Includes customerId: 1; numeric amounts; Oslo wall time.
});
```

Manual Stage 12 verification:

1. Start PostgreSQL, the Java backend on `127.0.0.1:8080`, and the frontend on `127.0.0.1:3000` using the setup commands above. Open [Create Request](http://127.0.0.1:3000/requests/new).
2. Click **Post request** with the form empty. Expect seven inline errors (budget is optional), focus on the error summary, and no saved row.
3. Choose **Shopping**; enter title **Buy groceries**, description **Milk, eggs and bread. Please keep the receipt.**, pickup **KIWI Majorstuen, Oslo**, delivery **Frogner, Oslo**, budget **300.25**, reward **80.50**, and deadline **15 January 2027, 18:00** in Oslo. Use a later date if this example is no longer in the future.
4. Before posting, change reward to **-1** and submit. Expect a money error with all entries retained. Restore **80.50**, set the deadline to **15 January 2020, 18:00**, and submit. Expect the backend's **must be a future date** field error and no saved row.
5. Restore the future deadline and click **Post request** once. Expect a disabled **Posting request…** button, followed by **Request created**, a generated ID, **OPEN**, **Waiting for a helper**, budget **300.25 NOK**, reward **80.50 NOK**, and the same Oslo deadline. Fast responses may make the loading state brief. Each successful submission creates one row.
6. In [Swagger UI](http://127.0.0.1:8080/docs), execute `GET /api/requests/{id}` using the ID from the confirmation. Confirm `customerId: 1`, `helperId: null`, the saved values, and deadline `2027-01-15T17:00:00Z` for the winter example.
7. Click **Create another request**. Expect blank inputs with Category focused. For a separate optional-budget check, choose **Pickup**, fill the text fields and a future deadline, leave budget empty, and set reward to **0**. After submission, expect **Not specified** and **0.00 NOK**.
8. For a local connectivity check, stop only your Java backend, keep the frontend running, fill a valid form, and submit. Expect an error, retained values, and an enabled button. Restart Java afterward. Do not retry a timed-out submission until checking whether it was saved.
9. At a 390px mobile width, check the form and confirmation stack into one column without horizontal scrolling. Use Tab to reach each input and the submit button; errors and successful confirmation should receive focus.

Stage 12 automated checks: `npm run lint`, `npm run typecheck`, `npm run test:unit` (45 cases), `npm run build`, `npm test` with both servers running (53 cases), and `./mvnw test` in `backend` (250 cases). Tests validate the payload contract, fixed customer ID, all categories, optional/zero amounts, text limits, invalid money, and winter/summer Oslo display. The live route test now checks the actual form fields and labels while preserving the three later-stage placeholders.

## Stage 13 — available jobs

Open [Available Jobs](http://127.0.0.1:3000/jobs). The page fetches `GET /api/requests` through the existing API helper and preserves the backend's newest-first order. Each card displays category, title, pickup, delivery, the exact two-decimal reward in NOK, deadline in Oslo time, and OPEN status. The whole card is a keyboard-accessible link to `/requests/{id}`. The backend selects OPEN requests; this stage does not introduce an additional deadline filter.

The list is a Server Component inside `Suspense`, allowing **Loading available jobs…** to appear while the request is in progress. `connection()` keeps the fetch at request time, outside build-time prerendering; the API helper uses `cache: "no-store"` and a ten-second timeout. **Refresh jobs** and **Try again** use `router.refresh()` in a transition, disabling the button and showing **Refreshing…** until the fetch completes.

An empty array shows **No jobs available yet** and a link to Create Request. HTTP errors show the safe API message; connection/timeouts show a service-unavailable message. Invalid response data produces an error instead of broken cards or a misleading empty state. The response guard checks only fields used by the cards, including valid unique IDs, OPEN status, known categories, decimal reward strings, and deadlines with explicit offsets.

Important code:

```tsx
<Suspense fallback={<JobsLoading />}>
  <AvailableJobs />
</Suspense>

// Inside the async list component:
await connection();
const response = await apiRequest<unknown>("/requests");
if (!isAvailableJobs(response)) {
  throw new ApiError(502, "The server returned an invalid jobs response. Please try again.");
}
```

Manual Stage 13 verification:

1. Start the database, Java backend, and frontend using the commands above. Open [Available Jobs](http://127.0.0.1:3000/jobs), or follow **View available jobs** from Home.
2. Compare the cards with [the API response](http://127.0.0.1:8080/api/requests). Expect the same count and order, only OPEN statuses, matching category/title/addresses, two-decimal rewards, and deadlines displayed in Oslo time. The existing development data has five OPEN requests at this stage's verification.
3. Click **Refresh jobs**. Expect **Refreshing…** while loading and an updated list afterward. With a fast local API this state can be brief.
4. Tab to a card and press Enter. Its URL should be `/requests/{id}`. The Stage 14 detail page now loads the request and shows **Accept Job** when OPEN. Use **Back to available jobs** or the navigation link to return. At the original Stage 13 checkpoint, this route was intentionally not implemented.
5. At a 390px mobile width, expect one column, wrapping text, visible rewards/deadlines, and no horizontal scrolling. At desktop width, expect two columns.
6. To check recovery locally, stop only the Java backend, then click **Refresh jobs**. Expect **We couldn’t load available jobs** and **Try again**. Restart Java and click **Try again**; the cards should return. Existing records are unchanged by these reads.
7. To check an empty list without deleting data, use a separate test backend returning `[]` from `GET /api/requests`. Set `NEXT_PUBLIC_API_URL` for a temporary frontend instance and restart/rebuild it (production public environment values are compiled at build time). Expect **0 open requests**, **No jobs available yet**, and **Create a request**. A delayed response checks loading; a 503 response checks the API message and retry. Restore the real backend configuration after testing.

Automated checks are `npm run lint`, `npm run typecheck`, `npm run test:unit` (52 cases), `npm run build`, `npm test` with both servers running (61 cases), and `./mvnw test` in `backend` (250 cases). New unit cases cover the jobs-response contract, malformed payloads, unsafe/duplicate IDs, unavailable statuses, money precision, and explicit-offset deadlines. The live test compares every displayed card's fields, order, and destination with the real API.

## Stage 14 — request details and Accept Job

Open a card on [Available Jobs](http://127.0.0.1:3000/jobs). `/requests/{id}` fetches `GET /api/requests/{id}` at request time and displays category, title, description, both addresses, budget, reward, Oslo deadline, status, customer/helper IDs, and creation/update times. Missing or invalid IDs show **Request not found** with a link back. A loading message appears during the server fetch; other failures offer **Try again**.

**Accept Job** appears only for OPEN requests. It sends `POST /api/requests/{id}/accept` with `{ "helperId": 2 }`, then performs a fresh GET and updates the card. Both controls are disabled while pending, and a synchronous guard prevents a rapid double-click from issuing a second POST. Notices receive keyboard focus and announce success/errors. **Refresh request** reloads the current details without accepting anything.

A 409 displays the backend's already-accepted/no-longer-open message and refreshes the saved status/helper. If that refresh fails or still says OPEN, acceptance stays disabled until a manual refresh succeeds. Network/timeouts and server failures also reconcile with GET, without automatically repeating a POST. If acceptance succeeds but its follow-up GET fails, the saved acceptance response remains visible with a refresh warning. Known business/validation errors retain the backend message. Responses are validated before rendering.

Important code in `frontend/src/lib/request-details.ts`:

```ts
const data = await apiRequest<unknown>(`/requests/${id}/accept`, {
  method: "POST",
  body: { helperId: 2 },
});
// Validate the accepted response, then fetch the latest saved details:
const current = await fetchRequest(id);
```

The dynamic route awaits Next.js's promised `params`; its async loader uses `connection()` inside `Suspense`. The existing API helper supplies no-store requests and a ten-second timeout. The client component owns only the displayed request and acceptance/refresh state. No backend endpoint, schema, or dependency change was needed. `allowImportingTsExtensions` lets the workflow import the same API module in both Next.js and Node's native TypeScript tests. Helper 2 remains the planned MVP identity; authentication is not implemented. Customer tracking is available in Stage 15 below.

Manual Stage 14 verification:

1. Start the database, Java backend, and frontend as above. Open [the accepted verification request](http://127.0.0.1:3000/requests/12). Expect ACCEPTED, **Helper #2 (you)**, no Accept Job control, and a working **Refresh request** button.
2. To repeat acceptance, create a clearly labelled demo request through [Create Request](http://127.0.0.1:3000/requests/new), using a future deadline. This creates one new record; use that record for the following steps.
3. Open its card from Available Jobs. Compare all details with `GET /api/requests/{id}` in Swagger, including exact two-decimal amounts and Oslo time. Open this same detail URL in a second tab before accepting.
4. In the first tab, click **Accept Job**. Expect **Accepting job…**, then a success notice, ACCEPTED status, helper 2, and removal of Accept Job. A fast local response may make loading brief. Reload and verify the assignment persists.
5. In the stale second tab, click **Accept Job**. Expect **Delivery request has already been accepted**, refreshed ACCEPTED status/helper 2, and no Accept Job button. This must not overwrite the first acceptance.
6. Follow **Back to available jobs**. The accepted demo should no longer be listed. Open `/requests/9223372036854775807` or `/requests/invalid-id` and expect the useful not-found page.
7. At 390px width, check wrapped content, one-column details, accessible controls, and no horizontal overflow. Use keyboard navigation to operate the controls; feedback should receive focus.
8. For fault checks, use an isolated fake API and a second frontend with a different `NEXT_PUBLIC_API_URL`. Check initial GET 503 → Try again → recovery; delayed POST with double-click → one POST; POST 409 followed by GET 503 → acceptance blocked until Refresh succeeds; and POST 200 followed by GET 503 → saved ACCEPTED details remain visible. Do not alter existing database records to simulate failures.

Automated checks: `npm run lint`, `npm run typecheck`, `npm run test:unit` (**74 cases**), `npm run build`, `npm test` with both servers running (**87 cases**), and `./mvnw test` in `backend` (**250 cases**). The 22 new unit cases cover the detail contract, IDs, successful acceptance and refresh, conflicts, uncertain outcomes, saved-response fallback, and business errors. Four added live tests cover the detail route, missing/invalid IDs, and proxied acceptance validation without mutations.

## Stage 15 — My Requests

Open [My Requests](http://127.0.0.1:3000/my-requests). The page uses `GET /api/users/1/requests`, with customer 1 as the planned MVP identity. It shows every request created by that customer, including OPEN, ACCEPTED, PICKED_UP, DELIVERING, DELIVERED, and CANCELLED. Each linked card displays title, category, status, helper ID (or **No helper assigned**), and deadline in Oslo time. Helper IDs reuse the existing response DTO; there is no new user-profile endpoint.

The backend filters by the **customer relationship**, not by helper or status, and orders by `createdAt DESC, id DESC`. Existing customers with no requests receive `200 []`; unknown customers receive `404 Customer not found.` Invalid, nonpositive, or out-of-range path IDs receive the established 422 error format. Controllers return the existing complete `DeliveryRequestResponse` DTO. Reads do not change records. The new endpoint also appears in Swagger/OpenAPI. This remains the unauthenticated MVP; customer IDs are not an authorization mechanism.

Important backend code:

```java
@Transactional(readOnly = true)
public List<DeliveryRequestResponse> listByCustomer(Long customerId) {
    if (!users.existsById(customerId)) {
        throw new ResourceNotFoundException("Customer not found.");
    }
    return requests.findByCustomer_IdOrderByCreatedAtDescIdDesc(customerId)
            .stream().map(DeliveryRequestResponse::from).toList();
}
```

The page uses a server component inside `Suspense`, with `connection()` keeping API requests out of build-time rendering. The existing helper uses `cache: "no-store"` and a ten-second timeout. **Refresh requests** calls `router.refresh()` in a transition and disables itself until the fetch completes. An empty array displays **No requests yet** and a link to Create Request. Failed requests show an error and **Try again**. The response guard rejects malformed records, duplicate IDs, and records belonging to another customer rather than displaying an incorrect list. The existing card styles provide two desktop columns and one mobile column.

```tsx
const response = await apiRequest<unknown>("/users/1/requests");
if (!isCustomerRequests(response)) {
  throw new ApiError(502, "The server returned invalid request data. Please try again.");
}
```

Manual Stage 15 verification:

1. Start PostgreSQL, the updated Java backend, and frontend using the commands above. If running an executable JAR, stop the old process, run `./mvnw package` in `backend`, then restart it. For production frontend use, stop the old process, run `npm run build`, then `npm run start` in `frontend`.
2. Open [My Requests](http://127.0.0.1:3000/my-requests) from the navigation. Compare the count, order, IDs, and fields with [customer 1’s API response](http://127.0.0.1:8080/api/users/1/requests). At verification, there are **10** customer-1 requests: five OPEN, two ACCEPTED, one DELIVERED, and two CANCELLED.
3. Confirm request 12 displays ACCEPTED and **Helper #2**, request 11 shows OPEN and **No helper assigned**, and the completed/cancelled requests remain visible. Compare [customer 2’s API response](http://127.0.0.1:8080/api/users/2/requests); its request 7 must not appear on customer 1’s page, even though customer 1 is that request’s helper.
4. Click **Refresh requests**. The button shows **Refreshing…** while pending, then the latest status/helper data appears. A fast local response can make this brief. Use Tab and Enter on a card to open its Stage 14 detail page, then return through My Requests navigation.
5. At 390px width, expect one column with wrapping titles and deadlines, usable controls, and no horizontal scrolling.
6. In Swagger, call `GET /api/users/{userId}/requests` with `9223372036854775807` and expect 404; use `0` and expect 422. The isolated backend tests verify `200 []` for an existing customer who has no requests.
7. For empty/loading/failure checks without modifying saved records, run a separate fake API and a temporary frontend on another port with its own `NEXT_PUBLIC_API_URL`. Test `[]`, a delayed response, a 503, malformed/mixed-owner data, and a response delayed beyond ten seconds. Expect the matching empty/loading/error state, then recover using **Try again** when valid data returns. Change a fixture’s status/helper and use Refresh to verify fresh data is displayed.

Validation commands: `npm run lint`, `npm run typecheck`, `npm run test:unit` (**80 tests**), `npm run build`, `npm test` with both servers running (**97 tests: 80 unit + 17 live**), and `./mvnw test` (**263 tests**). The 13 new backend cases cover ownership, all categories/statuses, nullable helpers, complete DTOs, empty/missing customers, invalid IDs, ordering, read-only behavior, and committed workflow updates. Six new frontend unit cases cover the tracking contract; four new live cases check rendered cards and proxied data/errors. Stage 16 helper actions are described below; they were not part of this Stage 15 verification.

## Stage 16 — My Deliveries (complete)

Implemented and verified: `GET /api/users/{userId}/deliveries`, a helper-filtered repository query, and the My Deliveries page for MVP helper 2. The endpoint returns complete DTOs ordered by `createdAt DESC, id DESC`, including assigned completed/cancelled requests. It excludes unassigned requests and jobs assigned to other helpers. Missing helpers return 404; invalid path IDs return 422. The existing status PATCH endpoint and transition rules are reused. No schema migration, dependency, authentication, or UI-polish work was added during Stage 16.

The page shows title, category, status, description, pickup/delivery addresses, reward, Oslo deadline, and a detail link. Next steps are:

| Current status | Button | PATCH status |
| --- | --- | --- |
| ACCEPTED | Mark Picked Up | PICKED_UP |
| PICKED_UP | Start Delivery | DELIVERING |
| DELIVERING | Mark Delivered | DELIVERED |
| DELIVERED or CANCELLED | No action | None |

The server fetch streams a loading message and provides retry after initial errors. The client board refreshes assigned jobs after an update, disables controls while a request is pending, and focuses feedback. Empty lists link to Available Jobs. A synchronous guard prevents duplicate submissions while pending. Conflicts and uncertain responses trigger a GET instead of an automatic PATCH retry. A saved update remains visible if its follow-up GET fails; further updates stay blocked until Refresh deliveries succeeds. Unexpected or foreign-helper data is rejected.

Important code:

```java
@Transactional(readOnly = true)
public List<DeliveryRequestResponse> listByHelper(Long helperId) {
    if (!users.existsById(helperId)) {
        throw new ResourceNotFoundException("Helper not found.");
    }
    return requests.findByHelper_IdOrderByCreatedAtDescIdDesc(helperId)
            .stream().map(DeliveryRequestResponse::from).toList();
}
```

```ts
const action = deliveryAction(request);
if (!action) return;
// Only a valid next action for helper 2 can reach this mutation:
const data = await apiRequest<unknown>(`/requests/${request.id}/status`, {
  method: "PATCH",
  body: { status: action.status },
});
// Validate the saved response, then refresh the helper's list.
const deliveries = await fetchDeliveries();
```

Verification completed on **2026-10-23** (automated tests ran on October 22):

- `./mvnw test`: **276 tests passed**, zero failures, errors, or skips, using isolated PostgreSQL test schemas. The 13 new cases cover helper filtering, complete DTOs, categories, terminal states, empty/missing helpers, invalid IDs, ordering, read-only behavior, customer tracking, and stale transitions.
- Frontend lint and TypeScript passed. `npm run build` passed in the working repository. `npm test` passed **126 tests: 105 unit + 21 live**, including 25 new workflow unit cases and four new live cases. Unit coverage includes timeout/network reconciliation, invalid responses, conflicts, and failed refreshes.
- Packaged and restarted the updated Java backend and production frontend. The new endpoint appears in OpenAPI. The page displays all five helper-2 assignments and excludes other helpers’ jobs; completed/cancelled cards have no status action.
- Browser verification used only the new labelled demo request **#13**: accept as helper 2, **Mark Picked Up → Start Delivery → Mark Delivered**, then reload. The delivered status also appears on My Requests. A second stale tab received the expected 409, refreshed its saved state, and did not overwrite it. Feedback received keyboard focus.
- Verified keyboard refresh and detail navigation, active navigation, and the **390px** mobile layout without horizontal overflow. The production tab reported no browser warnings or errors.
- An isolated fake API verified empty results, loading, initial 503 errors and retry recovery, disabled controls during a delayed update, and one PATCH despite a double-click. A successful PATCH followed by a failed GET retained the saved status and disabled further updates until manual refresh succeeded. A lost update response was reconciled from the latest list without repeating the PATCH. Both temporary test servers were stopped.
- Before/after PostgreSQL snapshots confirm all **two users and eleven pre-existing requests are unchanged**, including timestamps and versions. Only demo #13 was added; it is assigned to helper 2, DELIVERED, version 4. No schema migration or dependency was added. This completed the Stage 16 checkpoint before the Stage 17 UI work.

To test Stage 16 manually:

1. In `backend`, run `./mvnw test`. After it passes, stop any previous Java process, run `./mvnw -DskipTests package`, and start `java -jar target/local-delivery-0.1.0.jar --spring.profiles.active=dev`.
2. Stop any previous frontend process, then in `frontend` run `npm run build`, `npm run start`, and in another terminal `npm test`. Open [My Deliveries](http://127.0.0.1:3000/my-deliveries) and compare its cards with [helper 2’s list](http://127.0.0.1:8080/api/users/2/deliveries). Existing delivered/cancelled cards must have no action button; jobs assigned to helper 1 must be absent.
3. Create one clearly labelled demo request with a future deadline and accept it as helper 2. Use only this new record for mutation checks. Open My Deliveries in two tabs before changing it.
4. In the first tab, use **Mark Picked Up**, **Start Delivery**, then **Mark Delivered**, one at a time. Verify each saved status, the next button, and the matching status on My Requests. After DELIVERED, the card stays visible without an action button. Reload to confirm persistence.
5. In the stale second tab, attempt its old action. Expect a 409 message, refreshed saved status, and no overwritten state or automatic mutation retry.
6. Check refresh, keyboard operation, focused feedback, and the 390px mobile layout. Use an isolated fake API for delayed updates/double-clicks, empty lists, initial errors, conflicts, timeout/lost-response reconciliation, and successful PATCH followed by failed GET. Further actions must remain disabled until a successful refresh when the saved state is uncertain.
7. Compare before/after database snapshots: all pre-existing users and requests must remain unchanged, with only the labelled new demo advancing through the workflow. Stop temporary test servers.

## Stage 17 — Basic UI Improvements (complete)

The existing green-and-cream interface now has a clearer navigation bar, smaller headings on working pages, consistent rounded cards, and better spacing. The home welcome keeps its larger heading. On narrow screens, navigation becomes a two-column grid, cards and form fields stack, and action buttons fill the available width.

A shared `StatusBadge` displays **Open, Accepted, Picked up, Delivering, Delivered,** or **Cancelled** with a distinct background and readable text. The API enum is retained in `data-status`; screen readers receive “Status:” before the label. Color is not the only indicator. All five card surfaces use the same component: available jobs, customer requests, helper deliveries, request details, and creation confirmation.

The Create Request form is grouped into **What do you need?**, **Where is it going?**, and **Budget & timing**, with visible labels and hints. Required/optional behavior and submission logic are unchanged. Invalid fields have stronger borders, associated error messages, and the existing focused error summary. Loading views share a labelled spinner and decorative placeholder lines; reduced-motion CSS disables the spinner animation. Empty results and API errors use consistent panels with their existing next-step/retry controls.

Important code:

```tsx
<StatusBadge status={request.status} />
<Suspense fallback={<LoadingState message="Loading your deliveries…" />}>
  <Deliveries />
</Suspense>
```

```css
.status-badge[data-status="DELIVERED"] {
  background: #e4f1e4;
  color: #2e653a;
}
@media (prefers-reduced-motion: reduce) {
  .loading-indicator { animation: none; }
}
```

Verification on **2026-10-23**:

- `npm run lint`, `npm run typecheck`, `npm run test:unit` (**105 passed**), `npm run build`, and `npm test` (**126 passed: 105 unit + 21 live**) all passed. Existing card checks now verify both API status values and readable badge labels.
- `./mvnw test`: **276 passed**, zero failures, errors, or skips. Stage 17 does not change backend code or APIs.
- The production frontend was rebuilt and restarted. Browser checks covered all five navigation pages and request details, keyboard refresh/detail links, active navigation, 320px and 390px phone layouts, and a 768px tablet layout. No horizontal overflow was found; tested navigation links and form controls had at least 44px height.
- Submitting an incomplete form preserved the entered title, marked the six remaining required fields, linked each field to its error, and focused the alert. No request was created.
- An isolated fake API verified all six badges (text contrast **5.75:1–6.77:1**), the streamed loading placeholder and status announcement, disabled refresh while pending, empty results, mobile 503-error feedback, and successful retry. Both temporary test servers were stopped. The production tab reported no browser warnings or errors.
- A database snapshot matches the completed Stage 16 snapshot exactly: **2 users and 12 requests unchanged**, including timestamps and versions. No package, UI framework, schema migration, or new business workflow was added. This completed the Stage 17 checkpoint before Stage 18 integration testing.

Manual testing:

1. Open [Home](http://127.0.0.1:3000/) and visit every navigation link. The selected page should have a dark green tab and a matching heading. Use Tab and Enter to check keyboard navigation and the Skip to content link.
2. Open [Create Request](http://127.0.0.1:3000/requests/new). Check its three sections. Enter a title only, then click **Post request**. Expect a focused error summary, errors for the other six required fields, and the title still present. This does not create data.
3. Open [Available Jobs](http://127.0.0.1:3000/jobs), [My Requests](http://127.0.0.1:3000/my-requests), and [My Deliveries](http://127.0.0.1:3000/my-deliveries). Check card spacing, readable status badges, deadlines, and detail links. Delivered/cancelled cards must still have no status-update button.
4. Resize to 320px, 390px, and 768px. Check that navigation remains usable, cards/fields stack on phones, buttons fit, and the page does not scroll horizontally.
5. Use **Refresh jobs**, **Refresh requests**, or **Refresh deliveries**. On a delayed response, the refresh control becomes disabled. For initial page loads with a slow API, expect a labelled loading placeholder. With OS reduced-motion enabled, its spinner should stay still.
6. To inspect empty/error states without changing database records, run a separate local frontend against a fake API returning `[]` or HTTP 503. Expect a helpful empty panel or error panel with recovery controls; restore valid data and retry. Keep the production API configuration unchanged.

This completed the Stage 17 UI checkpoint.

## Stage 18 — Integration Testing (complete)

Added `DeliveryWorkflowIntegrationTest`, which starts Spring Boot on a random local port and uses Java’s HTTP client against the real controllers, services, repositories, migrations, and PostgreSQL database. Each run creates its own random test schema, checks that schema before resetting fixtures, and removes it afterward. Tests do not run inside a shared test transaction: fresh HTTP reads and direct SQL checks verify committed data.

Four cases cover:

- The complete journey for **BUY**, **PACKAGE**, and **PICKUP**: creation, OPEN listing and details, acceptance, assignment, pickup, delivery, completion, and customer tracking.
- Rejected acceptance/status actions followed by successful recovery: bypassing helper assignment, accepting twice, skipping stages, repeating stale actions, and trying to move backward from DELIVERED. Failed actions must leave the stored row and every tracking view unchanged.

Each successful step compares request details, Available Jobs, both customers’ request lists, and both helpers’ delivery lists. Control requests ensure unrelated jobs remain correctly filtered. The tests also check precise money values, unchanged request fields, timestamps, version increments, and untouched users/control rows.

Important code:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeliveryWorkflowIntegrationTest {
    // HTTP requests exercise committed application transactions in an isolated schema.
}
```

```java
for (String next : new String[]{"PICKED_UP", "DELIVERING", "DELIVERED"}) {
    JsonNode updated = update(id, next, 200);
    assertStableFields(created, updated);
    assertAllViews(updated, waiting, otherAssigned);
    assertStored(updated, ++version);
}
```

Verification completed on **2026-10-24** (test execution and browser workflow on October 23):

- `./mvnw -Dtest=DeliveryWorkflowIntegrationTest test`: **4 passed**.
- `./mvnw test`: **280 passed**, zero failures, errors, or skips.
- `npm run lint`, `npm run typecheck`, and `npm test`: all passed; **126 frontend tests** (105 unit + 21 live). The previously verified production build remains current because Stage 18 changes only tests and documentation.
- The browser completed all eleven required steps using **demo BUY request #14**: customer 1 opened Create Request and posted it; it appeared in Available Jobs; helper 2 opened and accepted it; it disappeared from OPEN jobs and appeared in My Deliveries; the helper marked Picked up, Delivering, and Delivered; customer 1 saw Delivered in My Requests.
- The customer page was refreshed after each helper action and displayed the matching status. Reloading My Deliveries preserved Delivered with no remaining status-action button. Both browser tabs reported no warnings or errors.
- The demo’s shopping budget is **325.75 NOK**, reward **80.50 NOK**, and deadline **15 January 2027 at 18:00 Oslo time**. Final API and PostgreSQL checks agree: BUY, customer 1, helper 2, DELIVERED, version 4.
- Database snapshots confirm **all 2 users and 12 pre-existing requests are unchanged**, including timestamps and versions. Only the clearly labelled demo #14 was added. No application-code fix was needed; no schema, dependency, or API change was made. Stage 19 has not been started.

Exact manual workflow:

1. With the Java backend on port 8080 and frontend on port 3000, open [Create Request](http://127.0.0.1:3000/requests/new).
2. Select **Shopping**. Use a new title such as `Stage 18 manual test — groceries`, description `Demo only; no real purchase or delivery is needed.`, pickup `Demo grocery shop, Oslo`, delivery `Demo home, Oslo`, shopping budget `325.75`, and reward `80.50`. Choose a future Oslo date/time with the native deadline control (for example 15 January 2027, 18:00 while that date remains in the future).
3. Click **Post request**. Expect **Request created**, the new request number, an Open badge, and the entered amounts. Note the new ID; do not modify earlier sample requests.
4. Open [Available Jobs](http://127.0.0.1:3000/jobs). Find the new title and open its card. Confirm the details, customer 1, no helper, and **Accept Job**.
5. Click **Accept Job**. Expect Accepted, Helper #2 (you), and the accept button to disappear.
6. Use **Back to available jobs**. The new request must be absent. Other open requests remain visible.
7. Open [My Deliveries](http://127.0.0.1:3000/my-deliveries). Find the same request with **Mark Picked Up**.
8. Click **Mark Picked Up**, then **Start Delivery**, then **Mark Delivered**, waiting for each confirmation. The badges must progress Picked up → Delivering → Delivered. After completion, no status-action button remains.
9. In another tab, open [My Requests](http://127.0.0.1:3000/my-requests). Click **Refresh requests** after each helper step. The new card must show Helper #2 and the matching status, ending in Delivered.
10. Reload both pages and open the request details again. Delivered, the helper, amounts, addresses, and deadline must remain saved.
11. From `backend`, run `./mvnw test`; from `frontend`, run `npm test` with both services running. The automated integration tests use disposable schemas and do not modify these development records.

Stop before Stage 19 until requested.

## Manual creation and viewing tests

1. Start PostgreSQL and the Java backend, then open [Swagger UI](http://127.0.0.1:8080/docs).
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
6. Execute `GET /api/requests/{id}` using the returned ID. Confirm the saved fields and UTC deadline match the POST response exactly. Amounts use two decimal places; timestamps use PostgreSQL's precision.
7. Make each of these changes separately to the original JSON: remove `title`, set `category` to `INVALID`, set `helperReward` to `-1`, or set `shoppingBudget` to `-1`. Each must return 422 and save nothing. Set `customerId` to `9223372036854775807` to check the 404 error.
8. Fetch request ID `9223372036854775807`; expect 404. Use `abc` as the ID to check 422.

Read-only terminal checks:

```bash
curl -i http://127.0.0.1:8080/api/health
curl -i http://127.0.0.1:8080/api/requests
curl -i http://127.0.0.1:8080/api/requests/1
curl -i http://127.0.0.1:8080/api/requests/9223372036854775807
```

## Data model and migration

User and request IDs use Java `Long` and PostgreSQL `BIGINT`. Customer/helper relationships use separate `@ManyToOne` mappings to User, with helper nullable. PostgreSQL native enums retain all three categories and six statuses: OPEN, ACCEPTED, PICKED_UP, DELIVERING, DELIVERED, CANCELLED. Email uniqueness, rating bounds, foreign keys, nonnegative amounts, and existing indexes remain enforced by PostgreSQL.

Money is `BigDecimal` / `NUMERIC(10,2)`. Entities use UTC `LocalDateTime`; the JDBC configuration binds these values directly to avoid shifts caused by the computer's timezone. JSON DTOs expose timestamps with a UTC offset. Entity lifecycle callbacks initialize timestamps and update `updatedAt` on ORM changes. Direct SQL maintenance updates must explicitly update `updatedAt` and increment `version` to participate in concurrency protection.

Flyway owns schema changes; `spring.jpa.hibernate.ddl-auto=validate` checks mappings without changing tables. For a fresh database, V1 creates the original schema, V2 widens IDs and normalizes timestamps, and V3 adds optimistic locking. For a legacy installation, the migration strategy recognizes exactly Alembic revision `0001`, baselines V1, and applies V2 and V3. A Java V2 installation applies only V3. Other legacy revisions and unknown nonempty schemas are rejected.

V2 preserves each timestamp's instant using `AT TIME ZONE 'UTC'` before storing it as a timestamp without timezone. The old `alembic_version` marker remains for provenance; Flyway now owns migration history in `flyway_schema_history`.

Before the live switch, backups were saved under the ignored `backups/` directory:

- `before-java-cutover.dump`: final PostgreSQL custom-format backup.
- `before-java-cutover-records.json`: complete record snapshot used for preservation checks.
- `python-backend-before-migration.tar.gz`: committed Python backend source from `3f4ae96`.

Both users and both existing requests were compared field by field after migration, including normalized timestamp instants. Do not run the old Python backend against the migrated database; recovering the old stack requires restoring its database backup into a separate database as well as restoring its source.

Before the Stage 8 migration, another local backup was saved to `backups/before-stage8-migration.dump`, with a record snapshot in `backups/before-stage8-records.json`. All two existing users and four requests were preserved, with existing requests receiving version zero. Only the newly created Stage 8 sample was accepted during live verification.

## Tests and verification

With PostgreSQL running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
./mvnw test
```

The 280 tests cover the complete real-HTTP customer/helper journey, rejected-action recovery across tracking views, helper-only delivery listing, customer-only request tracking across every status, deterministic ordering, tracking after acceptance/delivery updates, health, creation in all categories, all Stage 5 DTOs and field validation, exact creation-response consistency with committed data, customer/helper relationships, timestamps and timezone handling, enum values, database constraints, rollback, idempotent seeding, listing only OPEN requests in mixed datasets, complete details for all statuses, read operations leaving records unchanged, consistent business/validation/framework/server errors, populated legacy-schema migration through V3, acceptance, all status transitions, and real concurrent acceptance/status transactions.

Tests use unique `test_java_*` PostgreSQL schemas and drop only those schemas afterward. They do not clear development tables, substitute an in-memory database, or silently skip database failures.

With the frontend and Java backend running:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm test
```

The Java migration was verified on 2026-10-06 with 80 passing tests, the executable JAR running, migration V2 applied with existing records preserved, live health/list/detail/docs endpoints returning 200, and the frontend smoke test passing. The Python server was stopped and its implementation/configuration/tests were replaced by Java files.

Stage 5 completion added 34 workflow DTO validation cases and two numeric-category regression cases. Its suite passed all 116 tests with no failures or skips. The updated executable JAR was built and restarted on port 8080; live health, docs, request listing, and 422 validation responses were verified without changing saved requests.

The Stage 6 review added three precision regression cases, reproduced their failures before the fix, and passed all 119 backend tests after the fix.

The updated JAR was built and started on port 8080. Live verification returned 201 for creation, 404 for a missing customer, and 422 for each of missing title, invalid category, negative reward, and negative shopping budget. The creation response exactly matched the subsequent GET response. Request **4**, titled **Stage 6 verification — groceries**, remains available for inspection at `/api/requests/4`; existing requests were unchanged, and failed submissions created no rows.

Stage 7 verification on 2026-10-06 passed all 120 tests. The running application returned requests 5, 4, 2, and 1 in newest-first order, with each detail response matching its complete list entry. Missing IDs returned 404 and invalid IDs returned 422. Database snapshots before and after the live checks confirmed that every saved user and request was unchanged. No application-code change or restart was needed.

Stage 8 verification on 2026-10-06 passed all 155 tests with no failures or skips. The JAR was built and restarted, Flyway V3 applied successfully, and all existing records matched the saved backup. Live acceptance returned 200 for helper 2, removed request 6 from OPEN jobs, and persisted version 1. Missing resources returned 404, self-acceptance returned 400, invalid helper input returned 422, and repeated acceptance returned 409. Previous requests were unchanged.

Stage 9 verification on 2026-10-18 passed all 220 backend tests with no failures or skips, including 65 status cases. The first attempt found Docker stopped; after starting the existing PostgreSQL container, the complete suite passed. The updated JAR was packaged and started on port 8080. Health, Swagger UI, and OpenAPI returned 200. New request 8 completed acceptance, pickup, delivery, and completion with version 4; requests 9 and 10 exercised both cancellation paths with versions 1 and 2. Live checks confirmed 409 for invalid transitions, 404 for a missing request, and 422 for invalid input. Database snapshots confirmed all two preexisting users and six requests remained unchanged. Flyway remained at V3, with no schema migration required.

Stage 10 completion on 2026-10-19 verified a successful full run of 250 tests with zero failures, errors, or skips. The executable JAR was packaged and started, with health, Swagger UI, and OpenAPI returning 200. Eighteen live error checks verified consistent JSON for 400, 404, 405, 406, 409, 415, and 422 responses, including field errors and protocol headers. All two existing users and nine requests matched their database snapshot exactly, including timestamps and versions. No development records were created or changed. The schema remains at V3.

Stage 11 verification on 2026-10-19 passed frontend lint, TypeScript checking, the production build, all 32 frontend tests, and all 250 backend regression tests. The production frontend is running on `127.0.0.1:3000`. Browser checks exercised every navigation link, active-link updates, page content, keyboard skip navigation, and the Home/My Deliveries layouts at 390px width with no horizontal overflow. Browser logs showed no warnings or errors during these checks. Live API checks verified direct and proxied health, a missing-request error, and a proxied validation error. No successful mutation was sent to development data during Stage 11.

Stage 12 verification on 2026-10-20 passed frontend lint, TypeScript checking, the production build, all 53 frontend tests, and all 250 backend regression tests. Browser checks covered empty fields, invalid money, real backend future-deadline validation, successful creation, confirmation display, reset/focus, and the 390px mobile layout. An isolated temporary API and second frontend verified a delayed 503, disabled loading state, exactly one POST on double-click, null optional budget/zero reward serialization, and timeout recovery without automatic retries. Those temporary servers were stopped after verification. Request **11**, **Stage 12 verification — groceries**, was created through the production frontend; its saved amounts, customer, status, helper, and UTC deadline were checked through the Java API. Database snapshots confirmed that all two existing users and nine existing requests, including timestamps and versions, remained unchanged. No schema or backend source change was needed.

Stage 13 verification on 2026-10-20 passed frontend lint, TypeScript checking, the production build, all 61 frontend tests, and all 250 backend regression tests. The production jobs page displays five real OPEN requests. Browser checks exercised refresh, loading, empty results, a 503 error, invalid response handling, retry recovery, timeout handling, zero reward, summer Oslo time, card links, and the 390px mobile layout without horizontal overflow. Failure/empty checks used a temporary isolated API and frontend; both temporary servers were stopped afterward. Before/after database snapshots confirmed all two users and ten requests were identical, including timestamps and versions. No records were created or changed. No backend source, schema migration, or dependency change was required.

Stage 14 verification on 2026-10-21 passed frontend lint, TypeScript checking, the production build, all 87 frontend tests (74 unit and 13 live), and all 250 backend regression tests, with zero failures or skips. Browser checks verified real acceptance, a stale-tab 409, refresh, persisted helper assignment, removal from Available Jobs, not-found handling, keyboard feedback focus, and the 390px layout without horizontal overflow. An isolated temporary API/frontend also verified loading, initial 503/retry recovery, exactly one POST per double-click, a conflict with failed refresh, manual recovery, and a successful POST with a failed follow-up GET. Both temporary servers were stopped. The production acceptance tab had no browser warnings or errors. Request **12**, **Stage 14 verification — groceries**, is the only added development record and is ACCEPTED by helper 2 with version 1. Database snapshots confirmed all two users and ten pre-existing requests remained exactly unchanged, including timestamps and versions. Stage 14 ended at this checkpoint; Stage 15 verification is recorded below.

Stage 15 verification on 2026-10-21 passed frontend lint, TypeScript checking, the production build, all 97 frontend tests (80 unit and 17 live), and all 263 backend regression tests, with zero failures or skips. The rebuilt Java JAR and production frontend are running. Live checks verified customer filtering, complete DTOs, OpenAPI documentation, and the ten customer-1 cards against API data. Browser checks covered navigation, keyboard-operated detail links, active navigation, refresh, helper/deadline/status display, and 390px layouts without horizontal overflow. Isolated fixtures verified all six statuses, loading and disabled refresh, updated status/helper after refresh, empty results, 503 errors, invalid/mixed-owner payloads, ten-second timeout handling, and successful retry recovery. Both temporary servers were stopped; the production page reported no browser warnings or errors. Before/after PostgreSQL snapshots confirmed all two users and eleven requests were identical, including timestamps and versions. No development records were created or changed, and no schema migration or dependency was added. This was the completed Stage 15 checkpoint; the Stage 16 section records the subsequent helper-workflow verification.


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

The listing service explicitly selects OPEN requests and maps the results to DTOs:

```java
return requests.findByStatusOrderByCreatedAtDescIdDesc(DeliveryStatus.OPEN)
        .stream().map(DeliveryRequestResponse::from).toList();
```

`DeliveryRequestService.create` runs in a transaction, checks the customer, sets OPEN status, and returns the saved entity's values:

```java
var saved = requests.saveAndFlush(request);
entityManager.refresh(saved);
return DeliveryRequestResponse.from(saved);
```

Acceptance uses the same transaction and persistence pattern after checking eligibility:

```java
request.setHelper(helper);
request.setStatus(DeliveryStatus.ACCEPTED);
var saved = requests.saveAndFlush(request); // Checks @Version against the stored row.
entityManager.refresh(saved);
return DeliveryRequestResponse.from(saved);
```

The central domain rule includes both acceptance and status changes:

```java
public boolean canTransitionTo(DeliveryStatus next) {
    return switch (this) {
        case OPEN -> next == ACCEPTED || next == CANCELLED;
        case ACCEPTED -> next == PICKED_UP || next == CANCELLED;
        case PICKED_UP -> next == DELIVERING;
        case DELIVERING -> next == DELIVERED;
        case DELIVERED, CANCELLED -> false;
    };
}
```

`DeliveryRequestService.updateStatus` additionally requires the dedicated acceptance operation for ACCEPTED and an assigned helper for delivery progress. It saves and refreshes within a transaction, just like acceptance.

## Files changed and Git

This migration adds the Maven wrapper/build, Java packages, properties, Flyway scripts, and Java tests listed above. It removes the Python app, Python tests, requirements, and Alembic configuration/scripts. It updates `.env.example`, `.gitignore`, `compose.yaml`, and this README. Frontend source and existing credentials are unchanged.

Stage 5 completion adds `AcceptRequestDto.java`, `UpdateStatusRequest.java`, and `WorkflowDtoValidationTest.java`. It updates `application.properties` to reject numeric enums, extends `DeliveryApiTest.java` for regression coverage, and updates this README. No database migration is required.

Stage 6 review changes only `DeliveryRequestService.java`, `DeliveryApiTest.java`, and this README. It adds no endpoint or database migration.

Stage 7 review changes `DeliveryApiTest.java`, `ApiErrorTest.java`, and this README. It strengthens filtering, full-response, read-only, and error-response checks. The controller, service, repository, and database schema already satisfy this stage.

Stage 8 creates `RequestNotOpenException.java`, `SelfAcceptanceException.java`, `V3__request_optimistic_locking.sql`, and `AcceptRequestApiTest.java`. It updates `DeliveryRequestController.java`, `DeliveryRequestService.java`, `DeliveryRequest.java`, `ApiExceptionHandler.java`, `DeliveryApiTest.java`, `ApiErrorTest.java`, `LegacyMigrationTest.java`, and this README. The earlier uncommitted Stage 7 test/documentation changes are retained.

Stage 9 creates `InvalidStatusTransitionException.java` and `UpdateStatusApiTest.java`. It updates `DeliveryStatus.java`, `DeliveryRequestController.java`, `DeliveryRequestService.java`, `ApiExceptionHandler.java`, `DeliveryApiTest.java`, `ApiErrorTest.java`, and this README. The existing `UpdateStatusRequest` DTO is reused. There are no schema or frontend changes.

Stage 10 creates `RequestAlreadyAcceptedException.java` and `ApiErrorContractTest.java`. It updates `ApiExceptionHandler.java`, `DeliveryRequestService.java`, `AcceptRequestApiTest.java`, and this README. It reuses the existing error DTO and adds no dependency, schema migration, or frontend changes.

Stage 11 creates `frontend/.env.example`, `frontend/next.config.mjs`, the four feature page files, `navigation.tsx`, `page-shell.tsx`, `api-config.mjs`, `api.ts`, and `tests/api.test.mjs`. It updates the root layout, homepage, global CSS, `tests/home.test.mjs`, frontend package/lock files, and this README. Node 22.17+ is declared for native TypeScript tests; no package dependency is added. Local `frontend/.env.local` is created from the template and remains ignored. Java source and database schema are unchanged.

Stage 12 files:

- Added `frontend/src/components/create-request-form.tsx`: accessible form, loading/error handling, and saved confirmation.
- Added `frontend/src/lib/create-request.ts`: typed payload/response, validation, and Oslo date formatting.
- Added `frontend/tests/create-request.test.mjs`: 21 request payload, validation, and date-format tests.
- Updated `frontend/src/app/requests/new/page.tsx`: renders the form instead of the placeholder.
- Updated `frontend/src/app/globals.css`: form controls, feedback, confirmation, and mobile layout.
- Updated `frontend/tests/home.test.mjs`: verifies the new form and labels on the running page.
- Updated `frontend/package.json`: includes request-form tests in `test:unit`.
- Updated `README.md`: Stage 12 behavior, verification, and manual steps. No dependency was added.

Stage 13 files:

- Added `frontend/src/components/available-jobs.tsx`: server fetch, loading/empty/error states, and linked cards.
- Added `frontend/src/components/refresh-jobs-button.tsx`: client refresh/retry transition.
- Added `frontend/src/lib/available-jobs.ts`: type and response validation for displayed jobs.
- Added `frontend/tests/available-jobs.test.mjs`: seven response-contract tests.
- Updated `frontend/src/app/jobs/page.tsx`: page heading and Suspense boundary.
- Updated `frontend/src/app/globals.css`: job cards, toolbar, feedback, and mobile grid.
- Updated `frontend/tests/home.test.mjs`: live card/API comparison and jobs route assertions.
- Updated `frontend/package.json`: includes jobs-response tests in `test:unit`.
- Updated `README.md`: behavior, implementation, testing, scope, and file list.

Stage 14 files:

- Added `frontend/src/app/requests/[id]/page.tsx`: dynamic server fetch, loading, and retry.
- Added `frontend/src/app/requests/[id]/not-found.tsx`: useful missing-request page.
- Added `frontend/src/components/request-details.tsx`: complete details, acceptance/refresh controls, loading guard, and focused feedback.
- Added `frontend/src/lib/request-details.ts`: ID/response validation and acceptance reconciliation.
- Added `frontend/tests/request-details.test.mjs`: 22 workflow and response-contract tests.
- Updated `frontend/tests/home.test.mjs`: four live detail/proxy checks.
- Updated `frontend/src/app/globals.css`: detail width and wrapping action controls.
- Updated `frontend/package.json` and `frontend/tsconfig.json`: native workflow test inclusion and TypeScript import support.
- Updated `README.md`: scope, behavior, verification, manual steps, and changed files.

Stage 15 files:

- Added `backend/src/main/java/com/localdelivery/controller/UserRequestController.java`: validated customer request endpoint.
- Updated `backend/src/main/java/com/localdelivery/service/DeliveryRequestService.java`: read-only customer lookup and DTO mapping.
- Updated `backend/src/main/java/com/localdelivery/repository/DeliveryRequestRepository.java`: customer filter and deterministic newest-first order.
- Added `backend/src/test/java/com/localdelivery/CustomerRequestsApiTest.java`: 13 PostgreSQL-backed endpoint tests.
- Updated `frontend/src/app/my-requests/page.tsx`: working tracking page with loading boundary.
- Added `frontend/src/components/customer-requests.tsx`: data fetch, cards, empty/error states.
- Added `frontend/src/components/refresh-requests-button.tsx`: refresh/retry transition.
- Added `frontend/src/lib/customer-requests.ts`: response and customer-ownership validation.
- Added `frontend/tests/customer-requests.test.mjs`: six response-contract cases.
- Updated `frontend/tests/home.test.mjs` and `frontend/package.json`: live tracking checks and unit-test inclusion.
- Updated `README.md`: scope, endpoint behavior, code, file list, and verification steps. No dependency or schema change was required.

Stage 16 files:

- Added `backend/src/main/java/com/localdelivery/controller/UserDeliveryController.java`: helper delivery endpoint.
- Updated `backend/src/main/java/com/localdelivery/service/DeliveryRequestService.java` and `repository/DeliveryRequestRepository.java`: read-only helper filtering and ordering.
- Added `backend/src/test/java/com/localdelivery/HelperDeliveriesApiTest.java`: 13 endpoint and workflow cases.
- Updated `frontend/src/app/my-deliveries/page.tsx`: server loading/error states and delivery board.
- Added `frontend/src/components/delivery-board.tsx`: assigned-job cards, status actions, refresh, feedback, and duplicate-submit guard.
- Added `frontend/src/lib/helper-deliveries.ts`: validation, action mapping, mutation, and reconciliation.
- Added `frontend/tests/helper-deliveries.test.mjs`: 25 unit cases.
- Updated `frontend/src/app/globals.css`, `frontend/package.json`, and `frontend/tests/home.test.mjs`: card layout, test inclusion, and live action/proxy checks.
- Updated `README.md`: implementation details, successful verification, manual test steps, and commit suggestion. Earlier uncommitted Stage 15 work is preserved.

Stage 17 files:

- Added `frontend/src/components/status-badge.tsx`: shared readable labels and status-specific styling hooks.
- Added `frontend/src/components/loading-state.tsx`: shared accessible loading placeholder.
- Updated `frontend/src/app/globals.css`: navigation, typography, cards, badge colors, form sections, feedback panels, responsive layouts, reduced-motion and forced-color rules.
- Updated `frontend/src/app/page.tsx`: home-specific heading treatment.
- Updated `frontend/src/app/my-requests/page.tsx`, `frontend/src/app/my-deliveries/page.tsx`, and `frontend/src/app/requests/[id]/page.tsx`: shared loading state.
- Updated `frontend/src/components/available-jobs.tsx`, `frontend/src/components/customer-requests.tsx`, `frontend/src/components/delivery-board.tsx`, and `frontend/src/components/request-details.tsx`: shared status badges; available jobs also uses the shared loading state.
- Updated `frontend/src/components/create-request-form.tsx`: three labelled form sections and the shared confirmation badge.
- Updated `frontend/tests/home.test.mjs`: verify readable status labels in the existing live card tests.
- Updated `README.md`: scope, important code, verification, manual checks, file list, and commit suggestion. Earlier uncommitted Stage 15/16 work is preserved.

Stage 18 files:

- Added `backend/src/test/java/com/localdelivery/DeliveryWorkflowIntegrationTest.java`: four complete-flow HTTP integration cases with isolated PostgreSQL data and cross-view persistence assertions.
- Updated `README.md`: test coverage, browser results, important code, exact reproduction steps, and commit suggestion.

`.env`, build outputs, dependency folders, IDE files, and database backups are ignored. No commit or push is made automatically. Review with `git status` and `git diff` from the project root.

Suggested commit:

```text
test: add end-to-end delivery workflow coverage
```

Stage 18 is complete. Stop before Stage 19 until requested.
