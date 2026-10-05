from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.database import get_engine
from app.models import User

TEST_USERS = (
    (1, "Customer Test", "customer@example.com"),
    (2, "Helper Test", "helper@example.com"),
)


def seed_users(session: Session) -> None:
    """Add the two demo accounts without overwriting existing users."""
    for user_id, name, email in TEST_USERS:
        existing = session.get(User, user_id)
        email_owner = session.scalar(select(User).where(User.email == email))
        if existing is not None:
            if existing.email != email:
                raise ValueError(f"Demo user ID {user_id} is already in use.")
            continue
        if email_owner is not None:
            raise ValueError(f"Demo email for user {user_id} is already in use.")
        session.add(User(id=user_id, name=name, email=email))

    session.flush()
    # Explicit demo IDs must not collide with the next automatically assigned ID.
    session.execute(text("""
        SELECT setval(
            pg_get_serial_sequence('users', 'id'),
            GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_id_seq)),
            true
        )
    """))


def main() -> None:
    with Session(get_engine()) as session, session.begin():
        seed_users(session)
    print("Demo users ready: 1 = Customer Test, 2 = Helper Test.")


if __name__ == "__main__":
    main()
