from collections.abc import Generator
from pathlib import Path
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import Engine, create_engine, text

from app.config import get_database_url

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
