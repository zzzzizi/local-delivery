from pathlib import Path

from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import Engine, inspect, text

from app.database import Base


def test_migration_matches_models(database_engine: Engine) -> None:
    with database_engine.connect() as connection:
        context = MigrationContext.configure(connection)
        assert compare_metadata(context, Base.metadata) == []
        assert connection.scalar(text("SELECT version_num FROM alembic_version")) == "0001"


def test_migration_can_downgrade_and_upgrade(database_engine: Engine) -> None:
    config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
    with database_engine.begin() as connection:
        config.attributes["connection"] = connection
        command.downgrade(config, "base")
        assert "users" not in inspect(connection).get_table_names()
        assert "delivery_requests" not in inspect(connection).get_table_names()
        assert inspect(connection).get_enums() == []
        command.upgrade(config, "head")
        assert {"users", "delivery_requests"}.issubset(inspect(connection).get_table_names())
