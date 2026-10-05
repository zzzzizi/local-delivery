from alembic import context
from sqlalchemy import Connection

from app.database import Base, get_engine
from app import models  # noqa: F401 — register all model tables with Base.metadata

config = context.config


def run_migrations(connection: Connection) -> None:
    context.configure(connection=connection, target_metadata=Base.metadata)
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    from app.config import get_database_url

    context.configure(
        url=get_database_url(), target_metadata=Base.metadata, literal_binds=True
    )
    with context.begin_transaction():
        context.run_migrations()
else:
    # Tests supply their own connection to a temporary PostgreSQL schema.
    connection = config.attributes.get("connection")
    if connection is not None:
        run_migrations(connection)
    else:
        with get_engine().connect() as connection:
            run_migrations(connection)
