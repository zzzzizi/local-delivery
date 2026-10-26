# Local Delivery

A peer-to-peer delivery app for Oslo. Neighbours post small jobs (buy groceries, pick something up, deliver a package) and other users accept and complete them for a reward.

One account can act as both roles:

- **Customer** — create a delivery request and track its progress.
- **Helper** — browse open jobs, accept one, and move it through pickup → delivery → done.

The app is being prepared for a small user trial. Hosting hasn't been chosen yet, so nothing is publicly deployed. See the [deployment guide](deploy/README.md) and [trial checklist](deploy/TRIAL_CHECKLIST.md).

**Not included (yet):** payments, live GPS, maps, chat, and ratings.

---

## Quick start (Docker)

You only need Docker Desktop (or Docker Engine with Compose v2). Java and Node are built inside the images.

```bash
cp .env.example .env          # first time only, then set DB_PASSWORD in .env
docker compose up --build -d --wait
```

Then open:

| What | URL |
| --- | --- |
| Website | http://127.0.0.1:3000 |
| Email inbox (Mailpit, local only) | http://127.0.0.1:8025 |
| API docs (Swagger UI) | http://127.0.0.1:8080/docs |
| Health check | http://127.0.0.1:8080/api/health |

**Try it out:** register an account, open the verification email in the Mailpit inbox, confirm your address, then log in. To test the full flow, register a second account in another browser profile: one account creates a request, the other accepts and delivers it.

Common commands:

```bash
docker compose logs -f backend frontend   # follow logs
docker compose stop                       # stop, keep everything
docker compose up -d --wait               # start again
docker compose down                       # remove containers, keep the database
```

> ⚠️ Don't add `--volumes` to `docker compose down` unless you really want to delete the database.

All ports are bound to `127.0.0.1` only. If a port is already in use, change `BACKEND_PORT`, `FRONTEND_PORT`, `DB_PORT`, or `MAILPIT_PORT` in `.env`.

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Jakarta Validation, Maven Wrapper |
| Database | PostgreSQL, Flyway migrations, Hibernate schema validation |
| Frontend | Next.js 16, React 19, TypeScript, Biome |
| API docs | springdoc OpenAPI + Swagger UI |
| Email (local) | Mailpit captures all outgoing mail |
| CI | GitHub Actions |

The backend follows a standard layered design: controllers → transactional services → repositories → JPA entities. Controllers return DTOs, never entities.

```text
local-delivery/
├── docker-compose.yml        # db, mailpit, backend, frontend
├── .env.example              # copy to .env
├── backend/                  # Spring Boot API
│   └── src/main/java/com/localdelivery/
│       ├── controller/       # REST endpoints
│       ├── service/          # business logic, accounts, email, rate limiting
│       ├── repository/       # Spring Data repositories
│       ├── model/            # User, DeliveryRequest, enums
│       ├── dto/              # request/response records
│       ├── security/         # session handling
│       ├── exception/        # domain errors + ApiExceptionHandler
│       └── config/           # security, migrations, demo data
├── frontend/                 # Next.js app
│   ├── src/app/              # pages (jobs, my-requests, my-deliveries, login, ...)
│   ├── src/components/       # UI components
│   ├── src/lib/              # API client and data validation
│   └── tests/                # Node test runner tests
├── scripts/                  # Docker tests, backups, health checks
└── deploy/                   # trial deployment (Caddy, compose, checklist)
```

---

## Running without Docker (for development)

Use this when you want hot reload. You'll need **JDK 21** and **Node.js 22.17+**. No global Maven install is needed — `./mvnw` downloads it (use `mvnw.cmd` on Windows).

1. Keep the database and mail inbox in Docker, and stop the app containers:

   ```bash
   docker compose stop frontend backend
   docker compose up -d --wait db mailpit
   ```

2. Start the backend:

   ```bash
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Start the frontend in a second terminal:

   ```bash
   cd frontend
   cp .env.example .env.local    # first time only
   npm ci                        # first time only
   npm run dev
   ```

Use `127.0.0.1` rather than `localhost` in URLs — some machines resolve `localhost` to IPv6, which may reach a different service.

### Environment variables

The root `.env` is read by both Docker Compose and the backend. Use unquoted values.

| Variable | Default | Notes |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | |
| `DB_PORT` | `5433` | Host port for PostgreSQL |
| `DB_NAME` | `local_delivery` | |
| `DB_USERNAME` | `local_delivery` | |
| `DB_PASSWORD` | *(none)* | **Required.** Changing it later doesn't change an existing database's password. |
| `BACKEND_PORT` | `8080` | Host port for the API |
| `FRONTEND_PORT` | `3000` | Host port for the website |
| `MAILPIT_PORT` | `8025` | Local email inbox |
| `SMTP_CAPTURE_PORT` | `1025` | Local SMTP |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:3000` | Used for links in emails |
| `SESSION_COOKIE_SECURE` | `false` locally | Must be `true` when served over HTTPS |
| `SEED_DEMO_USERS` | `false` | Legacy demo users (no passwords) in the `dev` profile only |

The frontend's `frontend/.env.local` sets `NEXT_PUBLIC_API_URL` (e.g. `http://127.0.0.1:8080/api`) for host development. Never put secrets in `NEXT_PUBLIC_*` variables. Docker builds configure this automatically.

The older `POSTGRES_*` variable names still work, but prefer `DB_*` and don't define both.

---

## Testing

| What | Command | Needs |
| --- | --- | --- |
| Backend tests | `cd backend && ./mvnw test` | PostgreSQL running |
| Frontend lint | `cd frontend && npm run lint` | — |
| Frontend types | `cd frontend && npm run typecheck` | — |
| Frontend unit tests | `cd frontend && npm run test:unit` | — |
| Full Docker test | `node scripts/test-docker.mjs` | Docker running, `npm ci` in `frontend` |
| Deployment config check | `node scripts/test-deployment.mjs` | Docker running |

Backend tests create their own temporary `test_java_*` schemas and drop them afterwards, so they never touch your development data.

The full Docker test spins up a separate, throwaway copy of the app on random ports. It registers test accounts, checks emails, runs every delivery workflow, restarts containers to verify data survives, tests backup/restore, then cleans up only its own resources.

CI runs all of the above on every push and pull request (see [.github/workflows/ci.yml](.github/workflows/ci.yml)).

---

## How it works

### Delivery lifecycle

```text
OPEN ──accept──▶ ACCEPTED ──▶ PICKED_UP ──▶ DELIVERING ──▶ DELIVERED
  │                 │
  └──── cancel ─────┴──▶ CANCELLED
```

- Only **another user** can accept a request — you can't accept your own.
- Only the **assigned helper** can advance the status.
- Only the **customer** can cancel, and only before pickup.
- `DELIVERED` and `CANCELLED` are final.
- If two people act at the same time (e.g. two helpers accept the same job), exactly one wins and the other gets `409 Conflict`. This uses optimistic locking.

Request categories are `PACKAGE`, `BUY`, and `PICKUP`.

### Accounts and security

- Passwords are 12–128 characters, stored as salted PBKDF2 hashes.
- Users must verify their email before logging in. Password reset links are single-use and expire; resetting signs out other sessions.
- Login and email sending are rate-limited (HTTP `429` with `Retry-After`).
- Sessions are server-side, using an HttpOnly `DELIVERY_SESSION` cookie. They expire after 30 minutes of inactivity and are lost when the backend restarts (accounts and data are not).
- Every `POST`/`PATCH` needs a CSRF token. The frontend handles this automatically.
- Open jobs are visible to any signed-in user. Once accepted, a request is visible only to its customer and helper.

### Validation rules for new requests

- Title (max 200 chars), description, pickup and delivery addresses (max 500 chars) are required.
- Amounts are in NOK, non-negative, with up to two decimals. Shopping budget is optional; a zero reward is allowed.
- The deadline must be in the future. Times without a timezone are treated as Oslo time; responses use UTC.
- Unknown fields are rejected.

---

## API reference

Full interactive docs are at `/docs` (Swagger UI); the raw spec is at `/api/openapi.json`. All endpoints except auth, health, and docs require a signed-in session.

### Delivery requests

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/requests` | Open jobs, newest first |
| `POST` | `/api/requests` | Create a request as the signed-in customer |
| `GET` | `/api/requests/{id}` | Request details |
| `POST` | `/api/requests/{id}/accept` | Accept a job (body: `{}`) |
| `PATCH` | `/api/requests/{id}/status` | Change status, e.g. `{"status":"PICKED_UP"}` |
| `GET` | `/api/me/requests` | Requests I created |
| `GET` | `/api/me/deliveries` | Jobs I'm delivering |

### Accounts

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Get a CSRF token (`{token, headerName}`) |
| `POST` | `/api/auth/register` | `{name, email, password}` |
| `POST` | `/api/auth/login` | `{email, password}` |
| `POST` | `/api/auth/logout` | End the session |
| `GET` | `/api/auth/me` | Current user |
| `POST` | `/api/auth/verify-email` | `{token}` |
| `POST` | `/api/auth/resend-verification` | `{email}` |
| `POST` | `/api/auth/forgot-password` | `{email}` |
| `POST` | `/api/auth/reset-password` | `{token, password}` |

### Health

| Endpoint | Description |
| --- | --- |
| `GET /api/health` | Is the backend running? |
| `GET /api/health/ready` | Is the database reachable? (`503` if not) |

### Calling the API from a script

Keep cookies between calls, and fetch a CSRF token before each `POST`/`PATCH`:

```bash
JAR=cookies.txt
TOKEN=$(curl -s -c $JAR -b $JAR http://127.0.0.1:8080/api/auth/csrf | jq -r .token)
curl -s -c $JAR -b $JAR -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' \
  http://127.0.0.1:8080/api/auth/login
```

Fetch a fresh token after logging in or out.

### Errors

Every error uses the same JSON shape:

```json
{
  "timestamp": "2026-10-19T06:38:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Delivery request has already been accepted.",
  "fields": {}
}
```

| Status | Meaning |
| --- | --- |
| `400` | Not allowed (e.g. accepting your own request) |
| `401` | Not signed in |
| `403` | Signed in, but not your request |
| `404` | Not found |
| `409` | Invalid status change, or someone else got there first |
| `422` | Invalid input — see `fields` for details |
| `429` | Too many attempts — wait and retry |
| `503` | Database temporarily unavailable |

Internal details (stack traces, SQL errors) are never included in responses.

---

## Database

- Schema changes are managed by Flyway migrations in [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration) (V1–V5). They run automatically on startup.
- Money is stored as `NUMERIC(10,2)`; timestamps are stored in UTC.
- To back up the database, use [scripts/backup-database.sh](scripts/backup-database.sh). Restore steps are in the [deployment guide](deploy/README.md).
- Older demo users (from before accounts existed) are kept but have no password and can't log in. Register a new account instead.

---

## Deployment

The project includes a ready-to-use HTTPS trial setup (Caddy + Docker Compose), SMTP configuration, backups, and health checks. Remaining steps — choosing a host, domain, production email, offsite backups, and inviting users — are listed in:

- [deploy/README.md](deploy/README.md) — setup, backups, and operations
- [deploy/TRIAL_CHECKLIST.md](deploy/TRIAL_CHECKLIST.md) — pre-launch checklist
