from collections.abc import Generator
from pathlib import Path
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session
from fastapi.testclient import TestClient

from app.config import get_database_url
from app.database import get_db
from app.main import app
from app.seed import seed_users

ALEMBIC_CONFIG = Path(__file__).resolve().parents[1] / "alembic.ini"


@pytest.fixture
def database_engine() -> Generator[Engine, None, None]:
    """Run real migrations in a fresh schema; never clear development tables."""
    schema = f"test_{uuid4().hex}"
    admin = create_engine(get_database_url(), connect_args={"connect_timeout": 5})
    engine = create_engine(
        get_database_url(),
        connect_args={"options": f"-csearch_path={schema}", "connect_timeout": 5},
    )
    try:
        with admin.begin() as connection:
            connection.execute(text(f'CREATE SCHEMA "{schema}"'))
        with engine.begin() as connection:
            config = Config(str(ALEMBIC_CONFIG))
            config.attributes["connection"] = connection
            command.upgrade(config, "head")
        yield engine
    finally:
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE'))
        admin.dispose()


@pytest.fixture
def client(database_engine: Engine) -> Generator[TestClient, None, None]:
    with Session(database_engine) as session, session.begin():
        seed_users(session)

    def override_get_db() -> Generator[Session, None, None]:
        with Session(database_engine) as session:
            yield session

    app.dependency_overrides[get_db] = override_get_db
    try:
        with TestClient(app) as test_client:
            yield test_client
    finally:
        del app.dependency_overrides[get_db]
