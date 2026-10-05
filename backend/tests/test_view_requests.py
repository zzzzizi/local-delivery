from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Any

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import Engine
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from app.models import DeliveryRequest, RequestCategory, RequestStatus


def save_request(
    engine: Engine,
    *,
    status: RequestStatus = RequestStatus.OPEN,
    category: RequestCategory = RequestCategory.PACKAGE,
    created_at: datetime | None = None,
) -> int:
    with Session(engine) as session:
        request = DeliveryRequest(
            customer_id=1,
            helper_id=2 if status not in (RequestStatus.OPEN, RequestStatus.CANCELLED) else None,
            category=category,
            title="Deliver a gift",
            description="A small wrapped parcel.",
            pickup_address="Majorstuen, Oslo",
            delivery_address="Frogner, Oslo",
            shopping_budget=None,
            helper_reward=Decimal("80.25"),
            deadline=datetime.now(timezone.utc) + timedelta(days=1),
            status=status,
        )
        if created_at is not None:
            request.created_at = created_at
        session.add(request)
        session.commit()
        return request.id


def test_empty_request_list(client: TestClient) -> None:
    response = client.get("/requests")
    assert response.status_code == 200
    assert response.json() == []


def test_list_includes_all_categories_but_only_open_requests(
    client: TestClient, database_engine: Engine,
) -> None:
    open_ids = {
        save_request(database_engine, category=category)
        for category in RequestCategory
    }
    for request_status in RequestStatus:
        if request_status != RequestStatus.OPEN:
            save_request(database_engine, status=request_status)

    response = client.get("/requests")
    assert response.status_code == 200
    requests = response.json()
    assert len(requests) == 3
    assert {item["id"] for item in requests} == open_ids
    assert {item["category"] for item in requests} == {category.value for category in RequestCategory}
    assert all(item["status"] == "OPEN" for item in requests)
    assert all(item["helper_id"] is None for item in requests)
    assert all(item["pickup_address"] == "Majorstuen, Oslo" for item in requests)
    assert all(Decimal(item["helper_reward"]) == Decimal("80.25") for item in requests)


def test_list_is_newest_first_with_stable_order_for_ties(
    client: TestClient, database_engine: Engine,
) -> None:
    newest_time = datetime.now(timezone.utc)
    newest_id = save_request(database_engine, created_at=newest_time)
    oldest_id = save_request(database_engine, created_at=newest_time - timedelta(days=1))
    tied_newest_id = save_request(database_engine, created_at=newest_time)

    response = client.get("/requests")
    assert response.status_code == 200
    assert [item["id"] for item in response.json()] == [tied_newest_id, newest_id, oldest_id]


@pytest.mark.parametrize("request_status", list(RequestStatus))
def test_get_request_returns_all_fields_for_every_status(
    client: TestClient, database_engine: Engine, request_status: RequestStatus,
) -> None:
    request_id = save_request(database_engine, status=request_status)

    response = client.get(f"/requests/{request_id}")
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {
        "id", "customer_id", "helper_id", "category", "title", "description",
        "pickup_address", "delivery_address", "shopping_budget", "helper_reward",
        "deadline", "status", "created_at", "updated_at",
    }
    assert body["id"] == request_id
    assert body["customer_id"] == 1
    assert body["helper_id"] == (
        None if request_status in (RequestStatus.OPEN, RequestStatus.CANCELLED) else 2
    )
    assert body["category"] == "PACKAGE"
    assert body["title"] == "Deliver a gift"
    assert body["description"] == "A small wrapped parcel."
    assert body["pickup_address"] == "Majorstuen, Oslo"
    assert body["delivery_address"] == "Frogner, Oslo"
    assert body["shopping_budget"] is None
    assert Decimal(body["helper_reward"]) == Decimal("80.25")
    assert body["status"] == request_status.value
    with Session(database_engine) as session:
        saved = session.get(DeliveryRequest, request_id)
        assert saved is not None
        for field in ("deadline", "created_at", "updated_at"):
            assert datetime.fromisoformat(body[field]) == getattr(saved, field)


def test_created_request_can_be_listed_and_retrieved(client: TestClient) -> None:
    payload = {
        "customer_id": 1,
        "category": "BUY",
        "title": "Buy groceries",
        "description": "Please buy milk and bread.",
        "pickup_address": "KIWI Majorstuen, Oslo",
        "delivery_address": "Frogner, Oslo",
        "shopping_budget": "300.10",
        "helper_reward": "80.25",
        "deadline": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat(),
    }
    created = client.post("/requests", json=payload)
    assert created.status_code == 201
    request_id = created.json()["id"]

    detail = client.get(f"/requests/{request_id}")
    listing = client.get("/requests")
    assert detail.status_code == listing.status_code == 200
    assert listing.json() == [detail.json()]
    assert detail.json()["id"] == request_id
    assert detail.json()["title"] == payload["title"]
    assert detail.json()["status"] == "OPEN"
    assert Decimal(detail.json()["shopping_budget"]) == Decimal("300.10")
    assert Decimal(detail.json()["helper_reward"]) == Decimal("80.25")


def test_request_not_found(client: TestClient) -> None:
    response = client.get("/requests/2147483647")
    assert response.status_code == 404
    assert response.json() == {"detail": "Request not found."}


@pytest.mark.parametrize("request_id", ["0", "-1", "abc", "1.5", "2147483648"])
def test_invalid_request_id_returns_422(client: TestClient, request_id: str) -> None:
    response = client.get(f"/requests/{request_id}")
    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["path", "request_id"]


@pytest.mark.parametrize(("path", "method"), [
    ("/requests", "scalars"), ("/requests/1", "get"),
])
def test_database_unavailable_returns_safe_503(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, path: str, method: str,
) -> None:
    def unavailable(*args: Any, **kwargs: Any) -> None:
        raise OperationalError("SELECT", {}, Exception("private database details"))

    monkeypatch.setattr(Session, method, unavailable)
    response = client.get(path)
    assert response.status_code == 503
    assert response.json() == {
        "detail": "Database temporarily unavailable. Try again later."
    }
    assert "private database details" not in response.text
