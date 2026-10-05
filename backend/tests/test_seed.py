import pytest
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from app.models import User
from app.seed import seed_users


def test_seed_is_repeatable_and_advances_id_sequence(database_engine: Engine) -> None:
    for _ in range(2):
        with Session(database_engine) as session, session.begin():
            seed_users(session)

    with Session(database_engine) as session:
        users = session.scalars(select(User).order_by(User.id)).all()
        assert [(u.id, u.name, u.email) for u in users] == [
            (1, "Customer Test", "customer@example.com"),
            (2, "Helper Test", "helper@example.com"),
        ]
        assert all(u.phone is None and u.rating is None for u in users)
        next_user = User(name="Next User", email="next@example.com")
        session.add(next_user)
        session.commit()
        assert next_user.id > 2


def test_seed_does_not_overwrite_existing_user(database_engine: Engine) -> None:
    with Session(database_engine) as session, session.begin():
        session.add(User(id=1, name="Existing", email="existing@example.com"))

    with pytest.raises(ValueError, match="already in use"):
        with Session(database_engine) as session, session.begin():
            seed_users(session)

    with Session(database_engine) as session:
        saved = session.get(User, 1)
        assert saved is not None
        assert saved.email == "existing@example.com"
        assert session.get(User, 2) is None
