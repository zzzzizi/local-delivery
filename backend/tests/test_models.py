from datetime import datetime, timedelta, timezone
from decimal import Decimal

import pytest
from sqlalchemy import Engine, select, text
from sqlalchemy.exc import IntegrityError, DataError
from sqlalchemy.orm import Session

from app.models import DeliveryRequest, RequestCategory, RequestStatus, User


def make_request(customer: User) -> DeliveryRequest:
    return DeliveryRequest(
        customer=customer,
        category=RequestCategory.PACKAGE,
        title="Deliver a gift",
        description="A small wrapped parcel.",
        pickup_address="Majorstuen, Oslo",
        delivery_address="Frogner, Oslo",
        helper_reward=Decimal("80.25"),
        deadline=datetime.now(timezone.utc) + timedelta(days=1),
    )


def test_user_saved_and_retrieved(database_engine: Engine) -> None:
    with Session(database_engine) as session:
        user = User(
            name="Test Customer", email="test@example.com", phone="+4700000000",
            rating=Decimal("4.50"),
        )
        session.add(user)
        session.commit()
        user_id = user.id

    with Session(database_engine) as session:
        saved = session.get(User, user_id)
        assert saved is not None
        assert (saved.name, saved.email, saved.phone) == (
            "Test Customer", "test@example.com", "+4700000000",
        )
        assert saved.rating == Decimal("4.50")
        assert saved.created_at.tzinfo is not None


@pytest.mark.parametrize("category", list(RequestCategory))
def test_request_saved_and_retrieved(
    database_engine: Engine, category: RequestCategory,
) -> None:
    with Session(database_engine) as session:
        customer = User(name="Customer", email="customer@example.com")
        request = make_request(customer)
        request.category = category
        if category == RequestCategory.BUY:
            request.shopping_budget = Decimal("300.10")
        deadline = request.deadline
        session.add(request)
        session.commit()
        request_id, customer_id = request.id, customer.id

    with Session(database_engine) as session:
        saved = session.get(DeliveryRequest, request_id)
        assert saved is not None
        assert saved.customer_id == customer_id
        assert saved.customer.email == "customer@example.com"
        assert saved in saved.customer.requests_created
        assert saved.helper_id is None
        assert saved.helper is None
        assert saved.status == RequestStatus.OPEN
        assert saved.category == category
        assert saved.title == "Deliver a gift"
        assert saved.description == "A small wrapped parcel."
        assert saved.pickup_address == "Majorstuen, Oslo"
        assert saved.delivery_address == "Frogner, Oslo"
        assert saved.helper_reward == Decimal("80.25")
        assert saved.shopping_budget == (
            Decimal("300.10") if category == RequestCategory.BUY else None
        )
        assert saved.deadline == deadline
        assert saved.created_at.tzinfo is not None
        assert saved.updated_at.tzinfo is not None


@pytest.mark.parametrize("status", list(RequestStatus))
def test_status_values_and_helper_relationship(
    database_engine: Engine, status: RequestStatus,
) -> None:
    with Session(database_engine) as session:
        request = make_request(User(name="Customer", email="customer@example.com"))
        request.helper = User(name="Helper", email="helper@example.com")
        request.status = status
        session.add(request)
        session.commit()
        request_id = request.id

    with Session(database_engine) as session:
        saved = session.get(DeliveryRequest, request_id)
        assert saved is not None
        assert saved.status == status
        assert saved.helper is not None
        assert saved.helper_id == saved.helper.id
        assert saved.helper.email == "helper@example.com"
        assert saved in saved.helper.deliveries_accepted
        assert saved.customer.id != saved.helper.id


def test_updated_at_changes_on_orm_update(database_engine: Engine) -> None:
    with Session(database_engine) as session:
        request = make_request(User(name="Customer", email="customer@example.com"))
        session.add(request)
        session.commit()
        request_id = request.id
        created_at, updated_at = request.created_at, request.updated_at

    with Session(database_engine) as session:
        request = session.get(DeliveryRequest, request_id)
        assert request is not None
        request.title = "Updated gift delivery"
        session.commit()
        assert request.updated_at > updated_at
        assert request.created_at == created_at


def test_duplicate_email_rejected(database_engine: Engine) -> None:
    with Session(database_engine) as session:
        session.add_all([
            User(name="First", email="duplicate@example.com"),
            User(name="Second", email="duplicate@example.com"),
        ])
        with pytest.raises(IntegrityError):
            session.commit()


@pytest.mark.parametrize("field", ["helper_reward", "shopping_budget"])
def test_negative_amounts_rejected(database_engine: Engine, field: str) -> None:
    with Session(database_engine) as session:
        request = make_request(User(name="Customer", email="customer@example.com"))
        setattr(request, field, Decimal("-0.01"))
        session.add(request)
        with pytest.raises(IntegrityError):
            session.commit()


@pytest.mark.parametrize("rating", [Decimal("-0.01"), Decimal("5.01")])
def test_out_of_range_rating_rejected(database_engine: Engine, rating: Decimal) -> None:
    with Session(database_engine) as session:
        session.add(User(name="Customer", email="customer@example.com", rating=rating))
        with pytest.raises(IntegrityError):
            session.commit()


@pytest.mark.parametrize("field", ["customer_id", "helper_id"])
def test_nonexistent_user_rejected(database_engine: Engine, field: str) -> None:
    with Session(database_engine) as session:
        request = make_request(User(name="Customer", email="customer@example.com"))
        session.add(request)
        session.flush()
        setattr(request, field, 999999)
        with pytest.raises(IntegrityError):
            session.commit()


@pytest.mark.parametrize("field", ["category", "status"])
def test_invalid_enum_rejected_by_postgres(database_engine: Engine, field: str) -> None:
    with Session(database_engine) as session:
        request = make_request(User(name="Customer", email="customer@example.com"))
        session.add(request)
        session.commit()
        with pytest.raises(DataError):
            session.execute(
                text(f"UPDATE delivery_requests SET {field} = 'INVALID' WHERE id = :id"),
                {"id": request.id},
            )


def test_no_seed_requests_in_new_schema(database_engine: Engine) -> None:
    with Session(database_engine) as session:
        assert session.scalars(select(DeliveryRequest)).all() == []
