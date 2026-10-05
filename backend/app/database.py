from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import DeclarativeBase, Session

from app.config import get_database_url


class Base(DeclarativeBase):
    pass


@lru_cache
def get_engine() -> Engine:
    return create_engine(
        get_database_url(), pool_pre_ping=True, connect_args={"connect_timeout": 5}
    )


def get_db() -> Generator[Session, None, None]:
    """Provide one session per API request; callers commit explicitly."""
    with Session(get_engine()) as session:
        yield session
