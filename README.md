# Local Delivery — Stage 4

A local peer-to-peer delivery project for Oslo, built in small, tested stages.

Stages 1–4 are complete. The project has a FastAPI backend, a Next.js setup page, PostgreSQL, SQLAlchemy models, Alembic migrations, two demo users, and APIs to create, list, and retrieve delivery requests. Stage 5 has not started; accepting jobs comes later.

## Requirements

- Python 3.11 or newer
- Node.js 20.9 or newer and npm
- Docker Desktop with Docker Compose, running
- Free ports 8000 (backend), 3000 (frontend), and 5433 (this project's database)

Tested on macOS with Python 3.13.5, Node.js 22.17.0, and the official `postgres:17-alpine` image. Port 5433 was chosen because an existing unrelated PostgreSQL service already uses 5432. This project's container binds only to localhost and stores data in its own named Docker volume.

## Files

```text
local-delivery/
├── .env.example
├── .gitignore
├── compose.yaml
├── README.md
├── backend/
│   ├── alembic.ini
│   ├── requirements.txt
│   ├── pytest.ini
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py
│   │   ├── config.py
│   │   ├── database.py
│   │   ├── seed.py
│   │   ├── api/
│   │   │   ├── __init__.py
│   │   │   ├── health.py
│   │   │   └── requests.py
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── enums.py
│   │   │   ├── user.py
│   │   │   └── delivery_request.py
│   │   └── schemas/
│   │       ├── __init__.py
│   │       └── delivery_request.py
│   ├── migrations/
│   │   ├── env.py
│   │   ├── script.py.mako
│   │   └── versions/0001_create_users_and_requests.py
│   └── tests/
│       ├── conftest.py
│       ├── test_config.py
│       ├── test_create_request.py
│       ├── test_health.py
│       ├── test_migrations.py
│       ├── test_models.py
│       ├── test_seed.py
│       └── test_view_requests.py
└── frontend/
    ├── AGENTS.md
    ├── CLAUDE.md
    ├── src/app/{layout.tsx,page.tsx,globals.css}
    ├── tests/home.test.mjs
    ├── package.json
    ├── package-lock.json
    └── tsconfig.json
```

`local-delivery` is the project Git repository. `.env`, `.venv`, `node_modules`, `.next`, and generated Python/TypeScript caches are ignored. Frontend source is unchanged since Stage 1.

Stage 2 added `compose.yaml`, backend configuration/session helpers, four model files, the seed command, Alembic configuration and three migration files, and five database/configuration test files. It updated `.env.example`, `requirements.txt`, this README, and the health endpoint's docstring. A local ignored `.env` was created with a generated password; no password is stored in source control.

Stage 3 added `backend/app/api/requests.py`, `backend/app/schemas/__init__.py`, `backend/app/schemas/delivery_request.py`, and `backend/tests/test_create_request.py`. It updated `backend/app/main.py`, `backend/app/database.py` (docstring), `backend/tests/conftest.py`, `backend/requirements.txt` (direct Pydantic dependency), and this README. No database migration was needed.

Stage 4 updated `backend/app/api/requests.py` and this README, and added `backend/tests/test_view_requests.py`. No model, migration, dependency, or frontend changes were needed.

## Quick start on this machine

The project folder is `/Users/yingjizheng/Documents/ChatGPT/LD/local-delivery`. Its `.env` and dependencies are already configured.

Start the database and initialize it:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery
docker compose up -d --wait db
cd backend
source .venv/bin/activate
python -m alembic upgrade head
python -m app.seed
```

The seed command is repeatable. It creates exactly these demo users and refuses to overwrite unrelated users occupying their IDs/emails:

| ID | Name | Email |
| --- | --- | --- |
| 1 | Customer Test | customer@example.com |
| 2 | Helper Test | helper@example.com |

Start the backend in that terminal:

```bash
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Start the frontend in a second terminal:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm run dev
```

Open <http://localhost:3000>, <http://localhost:8000/health>, and <http://localhost:8000/docs>. The health response remains `{"status":"ok"}`. It reports API liveness, not database readiness; use `docker compose ps` and the migration checks below to verify the database.

If the servers are already running, use them directly. Stop an earlier instance with `Ctrl+C` before restarting it. Use `docker compose stop db` from the project folder to stop just this project's database while retaining its data. Restart with `docker compose up -d --wait db`.

## First-time setup on another machine

1. Start Docker Desktop.
2. From `local-delivery`, copy `.env.example` to `.env` and enter a unique `POSTGRES_PASSWORD`. Preserve the existing `.env` on this machine. No configuration needs to be copied into `backend`.
3. Install backend dependencies:

   ```bash
   cd backend
   python3 -m venv .venv
   source .venv/bin/activate
   python -m pip install -r requirements.txt
   ```

4. Install frontend dependencies with `npm ci` from `frontend`.
5. Follow the quick-start commands above using your own checkout path.

`app/config.py` loads the root `.env`; exported environment variables take precedence. It builds the PostgreSQL URL with `URL.create`, so passwords containing special characters work without manual URL escaping. Missing required settings produce an actionable error. Docker and Python use the same configuration.

The Docker volume persists across container restarts. Changing a password in `.env` alone does not change the credentials already stored in an initialized PostgreSQL volume.

## Database design

`User` stores `id`, `name`, `email`, `phone`, `rating`, and `created_at`. Email is unique. Phone and rating are nullable because the demo users have neither a supplied phone number nor a rating; a supplied rating must be between 0 and 5.

`DeliveryRequest` includes every requested field: `id`, `customer_id`, nullable `helper_id`, `category`, `title`, `description`, `pickup_address`, `delivery_address`, nullable `shopping_budget`, `helper_reward`, `deadline`, `status`, `created_at`, and `updated_at`.

- The customer and helper foreign keys both reference `users.id`, with separate ORM relationships in each direction.
- PostgreSQL enums store `PACKAGE`, `BUY`, `PICKUP`, and all six requested statuses.
- New requests default to `OPEN` at the database level. Status transition rules belong to later API stages.
- Money uses `NUMERIC(10, 2)` and Python `Decimal`, in NOK. Negative rewards and shopping budgets are rejected by database constraints.
- Timestamps use PostgreSQL timestamps with time zone. `created_at` and `updated_at` are initialized by the database; SQLAlchemy refreshes `updated_at` when a request is updated through the ORM. Direct SQL updates must set `updated_at` explicitly.
- Customer, helper, and status columns are indexed for future queries.

Alembic owns schema creation; the application does not call `create_all` at startup. Migration `0001` creates both tables, enum types, constraints, and indexes. Its downgrade removes them; rollback is tested only in disposable test schemas.

## Create a delivery request (Stage 3)

`POST /requests` validates the body, checks that the customer exists, saves the request in one database transaction, and returns the complete record with HTTP **201 Created**. The database assigns the ID, timestamps, and default `OPEN` status. The helper starts as `null`.

Validation rules:

- All fields below are required except `shopping_budget`, which can be omitted or `null` for any category.
- `customer_id` must be a positive integer referring to an existing user.
- Category must be `PACKAGE`, `BUY`, or `PICKUP`.
- Title, description, and addresses must contain non-whitespace text. Surrounding whitespace is trimmed; title length is limited to 200 characters and addresses to 500.
- Amounts must be finite, nonnegative, at most 99,999,999.99 NOK, and have no more than two decimal places. Zero reward is permitted. Response amounts are JSON strings to preserve decimal precision.
- The deadline must be in the future. Include an explicit timezone offset when possible. A datetime without one is interpreted in `Europe/Oslo`; responses use UTC.
- Extra fields are rejected, including client-supplied `id`, `status`, or `helper_id`.

To test in Swagger:

1. Start PostgreSQL and the backend using the quick-start commands.
2. Open <http://localhost:8000/docs#/requests/create_request_requests_post>.
3. Expand **POST /requests**, click **Try it out**, and paste this JSON. Change the deadline to a future date if you are testing later.

```json
{
  "customer_id": 1,
  "category": "BUY",
  "title": "Buy groceries",
  "description": "Please buy milk, bread and eggs.",
  "pickup_address": "KIWI Majorstuen, Oslo",
  "delivery_address": "Frogner, Oslo",
  "shopping_budget": 300,
  "helper_reward": 80,
  "deadline": "2026-10-06T18:00:00+02:00"
}
```

4. Click **Execute**. Expect HTTP **201**, a new `id`, `status: "OPEN"`, `helper_id: null`, and the saved request information.
5. Try `helper_reward: -1`, an invalid category, or remove the title. Each returns **422** with field-level details and saves no record.
6. Try `customer_id: 999999`. Expect **404** with `Customer not found.`

Database constraint conflicts return **409**. Temporary database connection failures return **503**. Failed transactions roll back, and database exception details are not included in those responses.

The live Swagger verification created request **1**, titled **Stage 3 Swagger test — groceries**, in the development database. It is intentionally left available for inspection. Additional successful submissions create additional records; request creation is not an upsert.

## View requests (Stage 4)

| Endpoint | Behavior |
| --- | --- |
| `GET /requests` | HTTP 200 with a JSON array of `OPEN` requests only; newest first, with descending ID as a tie breaker |
| `GET /requests/{request_id}` | HTTP 200 with the complete request, regardless of its status |

The list returns `[]` when no open jobs exist. It includes all three categories. Filters and pagination are not implemented in this stage. Detail responses include every field from the creation response, including customer/helper IDs, addresses, amounts, deadline, status, and timestamps. A valid but missing ID returns HTTP **404** and `{"detail":"Request not found."}`. IDs must be positive integers within PostgreSQL's integer range; invalid IDs return **422**. Both endpoints return a safe **503** response if the database is temporarily unavailable.

To test manually with both servers and PostgreSQL running:

1. Open <http://localhost:8000/requests>. Confirm a JSON array containing only `OPEN` requests. Existing requests are preserved; the live check found requests 2 and 1, newest first.
2. Open <http://localhost:8000/requests/1>. Confirm the complete record for the Stage 3 test request and HTTP 200.
3. Open <http://localhost:8000/requests/2147483647>. Expect HTTP 404 and `Request not found.`
4. Open <http://localhost:8000/requests/abc>. Expect HTTP 422 with the error pointing to `request_id`.
5. For Swagger, refresh <http://localhost:8000/docs>, expand either new **GET** operation, and select **Try it out → Execute**. Enter `1` for the detail operation. The list operation needs no parameters.

Equivalent terminal checks:

```bash
curl -i http://localhost:8000/requests
curl -i http://localhost:8000/requests/1
curl -i http://localhost:8000/requests/2147483647
```

If testing a fresh database with no requests, create one using Stage 3 first and use the returned ID. Status filtering is tested with all six statuses in isolated test schemas; Stage 4 adds no acceptance or status-update API.

## Run tests and checks

Start PostgreSQL first, then:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
.venv/bin/python -m pytest -q
.venv/bin/python -m alembic current
.venv/bin/python -m alembic check
.venv/bin/python -m pip check
```

Expect 88 passed tests, migration `0001 (head)`, and no schema changes detected. The health/configuration tests do not require PostgreSQL; run `pytest tests/test_health.py tests/test_config.py -q` for only those checks.

Database tests run against actual PostgreSQL using the configured connection. Each test creates a uniquely named temporary schema, applies the real migration, commits and reads records, then removes only that schema. Tests verify all categories/status values, relationships, decimal amounts, timestamps, constraints, repeatable seeding, ID allocation, schema/model agreement, and upgrade/downgrade. They never clear the development tables and do not fall back to SQLite or skip missing database connections. Use this local development database for tests; the configured role needs permission to create schemas.

Stage 3 adds 42 API tests covering successful creation in all categories, persistence from a separate database connection, required fields, invalid categories/rewards/budgets, text limits, customer IDs, deadlines, server-owned fields, optional values, unknown customers, and transaction rollback on database failures.

Stage 4 adds 18 tests for empty lists, filtering out every non-OPEN status, category coverage, deterministic ordering, full details for all six statuses, create/list/retrieve integration, missing requests, invalid IDs, and database-unavailable responses. To run only these tests, use `.venv/bin/python -m pytest tests/test_view_requests.py -q` from `backend`.

Frontend smoke test (with `npm run dev` running):

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm test
```

Stage 1 also provides `npm run lint`, `npm run typecheck`, and `npm run build`. Stop the frontend development server before a production build; `npm start` serves that build on port 3000.

## Manually inspect Stage 2

From the project folder, open a PostgreSQL shell without putting the password in a command:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Run:

```sql
\dt
SELECT version_num FROM alembic_version;
SELECT id, name, email FROM users ORDER BY id;
SELECT COUNT(*) FROM delivery_requests;
```

Expect the `users`, `delivery_requests`, and `alembic_version` tables; revision `0001`; and the two demo users above. The request count is zero before any API submissions. The live Stage 3 Swagger test added one request on this machine. Inspect it with `SELECT id, title, status, customer_id, helper_id FROM delivery_requests ORDER BY id;`.

To manually save and read a request without leaving sample data behind:

```sql
BEGIN;
INSERT INTO delivery_requests (
    customer_id, category, title, description,
    pickup_address, delivery_address, helper_reward, deadline
) VALUES (
    1, 'PACKAGE', 'Stage 2 test', 'A small test parcel',
    'Majorstuen, Oslo', 'Frogner, Oslo', 80.25, NOW() + INTERVAL '1 day'
) RETURNING id, customer_id, helper_id, status, helper_reward;
SELECT title, status FROM delivery_requests WHERE title = 'Stage 2 test';
ROLLBACK;
```

Expect `customer_id = 1`, a null helper, status `OPEN`, and reward `80.25`. `ROLLBACK` removes the test row. PostgreSQL sequences may retain gaps after rollbacks; this is normal. Exit with `\q`.

## Verification results

Verified on 2026-10-05:

| Check | Result |
| --- | --- |
| PostgreSQL container | Healthy, bound to `127.0.0.1:5433` |
| Alembic upgrade/current | `0001 (head)` applied |
| Alembic schema comparison | No new upgrade operations detected |
| Backend pytest | 88 passed |
| Python dependency check | No broken requirements |
| Development seed data | The two requested users |
| Live Swagger `POST /requests` | HTTP 201, request 1 saved with status OPEN |
| Live `GET /requests` | HTTP 200, existing OPEN requests returned newest first |
| Live `GET /requests/1` | HTTP 200; complete record also verified in Swagger |
| Live lookup for a missing ID | HTTP 404, `Request not found.` |
| Temporary test schemas after tests | Zero remaining |
| Frontend smoke test | 1 passed |
| Running API `/health` and `/docs` | HTTP 200 |

The Stage 1 production build, lint, type checking, and browser rendering also passed. Frontend code was not changed in Stages 2–4.

## Suggested Git commit

```text
feat: add request listing and detail endpoints
```

Stop here. Stage 5 will add job acceptance when requested.

## References

- [SQLAlchemy relationships](https://docs.sqlalchemy.org/en/21/orm/basic_relationships.html)
- [Alembic tutorial](https://alembic.sqlalchemy.org/en/latest/tutorial.html)
- [Official PostgreSQL Docker image](https://hub.docker.com/_/postgres)
- [Next.js installation](https://nextjs.org/docs/app/getting-started/installation)
- [FastAPI testing](https://fastapi.tiangolo.com/tutorial/testing/)
