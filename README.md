# Local Delivery — Stage 2

A local peer-to-peer delivery project for Oslo, built in small, tested stages.

Stages 1 and 2 are complete. The project has a FastAPI backend, a Next.js setup page, PostgreSQL, SQLAlchemy models, Alembic migrations, and two demo users. Stage 3 (the create-request API) has not started. Swagger still exposes only `GET /health`.

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
│   │   │   └── health.py
│   │   └── models/
│   │       ├── __init__.py
│   │       ├── enums.py
│   │       ├── user.py
│   │       └── delivery_request.py
│   ├── migrations/
│   │   ├── env.py
│   │   ├── script.py.mako
│   │   └── versions/0001_create_users_and_requests.py
│   └── tests/
│       ├── conftest.py
│       ├── test_config.py
│       ├── test_health.py
│       ├── test_migrations.py
│       ├── test_models.py
│       └── test_seed.py
└── frontend/
    ├── AGENTS.md
    ├── CLAUDE.md
    ├── src/app/{layout.tsx,page.tsx,globals.css}
    ├── tests/home.test.mjs
    ├── package.json
    ├── package-lock.json
    └── tsconfig.json
```

The containing `LD` workspace already has a Git repository. `.env`, `.venv`, `node_modules`, `.next`, and generated Python/TypeScript caches are ignored. Frontend source is unchanged in Stage 2.

Stage 2 added `compose.yaml`, backend configuration/session helpers, four model files, the seed command, Alembic configuration and three migration files, and five database/configuration test files. It updated `.env.example`, `requirements.txt`, this README, and the health endpoint's docstring. A local ignored `.env` was created with a generated password; no password is stored in source control.

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

## Run tests and checks

Start PostgreSQL first, then:

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
.venv/bin/python -m pytest -q
.venv/bin/python -m alembic current
.venv/bin/python -m alembic check
.venv/bin/python -m pip check
```

Expect 28 passed tests, migration `0001 (head)`, and no schema changes detected. The health/configuration tests do not require PostgreSQL; run `pytest tests/test_health.py tests/test_config.py -q` for only those checks.

Database tests run against actual PostgreSQL using the configured connection. Each test creates a uniquely named temporary schema, applies the real migration, commits and reads records, then removes only that schema. Tests verify all categories/status values, relationships, decimal amounts, timestamps, constraints, repeatable seeding, ID allocation, schema/model agreement, and upgrade/downgrade. They never clear the development tables and do not fall back to SQLite or skip missing database connections. Use this local development database for tests; the configured role needs permission to create schemas.

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

Expect the `users`, `delivery_requests`, and `alembic_version` tables; revision `0001`; the two demo users above; and zero delivery requests in a fresh setup.

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
| Backend pytest | 28 passed |
| Python dependency check | No broken requirements |
| Development seed data | Exactly the two requested users; zero delivery requests |
| Temporary test schemas after tests | Zero remaining |
| Frontend smoke test | 1 passed |
| Running API `/health` and `/docs` | HTTP 200 |

The Stage 1 production build, lint, type checking, and browser rendering also passed. Frontend code was not changed in Stage 2.

## Suggested Git commit

```text
feat: add user and delivery request database models
```

Stop here. Stage 3 will add request creation and API validation when requested.

## References

- [SQLAlchemy relationships](https://docs.sqlalchemy.org/en/21/orm/basic_relationships.html)
- [Alembic tutorial](https://alembic.sqlalchemy.org/en/latest/tutorial.html)
- [Official PostgreSQL Docker image](https://hub.docker.com/_/postgres)
- [Next.js installation](https://nextjs.org/docs/app/getting-started/installation)
- [FastAPI testing](https://fastapi.tiangolo.com/tutorial/testing/)
