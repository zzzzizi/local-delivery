# Local Delivery — Stage 1

A local peer-to-peer delivery project for Oslo, built in small, tested stages.

**Implemented:** a FastAPI backend, `GET /health`, pytest, SQLAlchemy installed for the next stage, and a Next.js/React/TypeScript frontend with a static setup page.

**Scope:** Stage 1 only. No PostgreSQL configuration, database models, delivery APIs, authentication, payments, or delivery workflow are implemented yet. The frontend and backend start independently; API integration belongs to Stage 7.

## Requirements

- Python 3.11 or newer (`python3 --version`)
- Node.js 20.9 or newer and npm (`node --version` and `npm --version`)
- Free local ports 8000 and 3000

This setup was tested on macOS with Python 3.13.5 and Node.js 22.17.0. PostgreSQL is not needed for Stage 1. Dependencies are installed locally, with Python packages in `backend/.venv` and JavaScript packages in `frontend/node_modules`.

## Files

```text
local-delivery/
├── .env.example
├── .gitignore
├── README.md
├── backend/
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py
│   │   └── api/
│   │       ├── __init__.py
│   │       └── health.py
│   ├── tests/
│   │   └── test_health.py
│   ├── pytest.ini
│   └── requirements.txt
└── frontend/
    ├── AGENTS.md
    ├── CLAUDE.md
    ├── src/app/
    │   ├── globals.css
    │   ├── layout.tsx
    │   └── page.tsx
    ├── tests/
    │   └── home.test.mjs
    ├── package.json
    ├── package-lock.json
    └── tsconfig.json
```

Generated `.venv`, `node_modules`, `.next`, and `next-env.d.ts` are ignored by Git. The containing `LD` workspace already has a Git repository, so no nested repository is needed. Every file above is new in Stage 1.

Next.js generates `frontend/AGENTS.md` and `frontend/CLAUDE.md` as guidance for future coding tools. They do not affect application behavior.

The environment example documents that Stage 1 needs no configuration or secrets. You do not need to copy it. Actual environment variables and database credentials will be introduced when they are needed; `.env` files are ignored.

## Run locally

Use two terminals. Commands below assume macOS/Linux and start from the project folder. On this machine that folder is `/Users/yingjizheng/Documents/ChatGPT/LD/local-delivery`.

**Terminal 1 — backend**

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

On subsequent runs, skip virtual-environment creation and installation unless dependencies change. Activate `.venv` and run the last command.

- API health: <http://localhost:8000/health>
- Interactive API documentation: <http://localhost:8000/docs>
- The bare backend URL (`/`) returns 404; the implemented route is `/health`.

**Terminal 2 — frontend**

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm ci
npm run dev
```

Open <http://localhost:3000>. On subsequent runs, only `npm run dev` is needed unless dependencies change.

Press `Ctrl+C` in each server terminal to stop it. If a port is already in use, stop the earlier instance before starting a new one.

## Run tests and checks

Backend (no running server or database required):

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/backend
.venv/bin/python -m pytest -q
.venv/bin/python -m pip check
```

The pytest test checks the HTTP status, JSON content type, and exact response body of `GET /health` through FastAPI's test client.

Frontend (keep `npm run dev` running in another terminal for `npm test`):

```bash
cd /Users/yingjizheng/Documents/ChatGPT/LD/local-delivery/frontend
npm test
npm run lint
npm run typecheck
```

The frontend smoke test requests the real running server and checks its HTTP status, HTML content type, page title, and `Local Delivery` heading. A connection error means the frontend server needs to be started first.

To check the production build, stop the development server first, then run:

```bash
npm run build
npm start
```

The production server also uses <http://localhost:3000>. The same `npm test` command works against it. Stop it before restarting development mode.

## Manual verification

1. Start both applications with the commands above.
2. Open <http://localhost:3000>. Confirm the page shows **Local Delivery**, **Oslo, Norway**, and **The foundation is in place.**
3. Open <http://localhost:8000/health>. Confirm the response is exactly `{"status":"ok"}`.
4. Open <http://localhost:8000/docs>. Expand **GET /health**, select **Try it out**, then **Execute**. Confirm HTTP **200** and the same JSON response.
5. For a terminal check, run `curl -i http://localhost:8000/health`. Expect HTTP 200 and `content-type: application/json`.

## Important backend code

`app/main.py` creates the FastAPI application and includes the separate health router. The route in `app/api/health.py` is:

```python
@router.get("/health")
def get_health() -> dict[str, Literal["ok"]]:
    """Report that the API is running; no database is needed in Stage 1."""
    return {"status": "ok"}
```

The frontend entry point is `src/app/page.tsx`, wrapped by `src/app/layout.tsx`. It is deliberately a static setup page for this stage.

## Suggested Git commit

```text
chore: initialize local delivery project
```

Stage 2 should only begin in a separate follow-up after this stage is working and tested.

## Stage 1 verification results

Verified on 2026-10-05:

| Check | Result |
| --- | --- |
| `.venv/bin/python -m pytest -q` | 1 passed, no test warnings |
| `.venv/bin/python -m pip check` | No broken requirements |
| SQLAlchemy import | Version 2.1.3 imported successfully |
| Live `GET /health` | HTTP 200, JSON `{"status":"ok"}` |
| Live `GET /docs` | HTTP 200 |
| `npm test` against `npm start` | 1 passed |
| `npm test` against `npm run dev` | 1 passed |
| `npm run lint` | Passed using Biome |
| `npm run typecheck` | Passed |
| `npm run build` | Production build succeeded |
| Browser verification | Setup page rendered successfully |
| npm dependency audit during final installation | 0 vulnerabilities |

Initial setup used `python3 -m venv .venv`, pip to install the backend requirements, and `npm install` to resolve JavaScript packages and create `package-lock.json`. Use `npm ci` for repeatable frontend installations now that the lockfile exists.

The initial test client emitted a deprecation warning; switching to Starlette's supported `httpx2` client resolved it. Biome replaced the initial ESLint dependency chain after npm flagged that chain. Local server startup and HTTP smoke tests required permission to access localhost outside the coding sandbox; both applications passed after that permission was granted.

## Framework references

- [Next.js installation](https://nextjs.org/docs/app/getting-started/installation)
- [FastAPI testing](https://fastapi.tiangolo.com/tutorial/testing/)
