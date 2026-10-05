from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Any
from zoneinfo import ZoneInfo

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import Engine, func, select
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from app.models import DeliveryRequest, RequestCategory, RequestStatus


@pytest.fixture
def payload() -> dict[str, Any]:
    return {
        "customer_id": 1,
        "category": "BUY",
        "title": "Buy groceries",
        "description": "Please buy milk, bread and eggs.",
        "pickup_address": "KIWI Majorstuen, Oslo",
        "delivery_address": "Frogner, Oslo",
        "shopping_budget": 300,
        "helper_reward": 80,
        "deadline": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat(),
    }


def request_count(engine: Engine) -> int:
    with Session(engine) as session:
        return session.scalar(select(func.count()).select_from(DeliveryRequest)) or 0


@pytest.mark.parametrize("category", list(RequestCategory))
def test_create_request_saves_and_returns_record(
    client: TestClient, database_engine: Engine, payload: dict[str, Any],
    category: RequestCategory,
) -> None:
    payload["category"] = category.value
    response = client.post("/requests", json=payload)

    assert response.status_code == 201
    body = response.json()
    assert body["id"] > 0
    assert body["customer_id"] == 1
    assert body["helper_id"] is None
    assert body["status"] == "OPEN"
    assert body["category"] == category.value
    assert Decimal(body["shopping_budget"]) == Decimal("300.00")
    assert Decimal(body["helper_reward"]) == Decimal("80.00")
    for field in ("title", "description", "pickup_address", "delivery_address"):
        assert body[field] == payload[field]
    assert datetime.fromisoformat(body["deadline"]) == datetime.fromisoformat(payload["deadline"])
    assert datetime.fromisoformat(body["created_at"]).tzinfo is not None
    assert datetime.fromisoformat(body["updated_at"]).tzinfo is not None

    # A separate database connection proves the HTTP transaction was committed.
    with Session(database_engine) as session:
        saved = session.get(DeliveryRequest, body["id"])
        assert saved is not None
        assert saved.title == payload["title"]
        assert saved.status == RequestStatus.OPEN
        assert saved.customer_id == 1
        assert saved.helper_id is None
        assert saved.helper_reward == Decimal("80.00")


@pytest.mark.parametrize("field", [
    "customer_id", "category", "title", "description", "pickup_address",
    "delivery_address", "helper_reward", "deadline",
])
def test_missing_required_field_rejected(
    client: TestClient, database_engine: Engine, payload: dict[str, Any], field: str,
) -> None:
    del payload[field]
    response = client.post("/requests", json=payload)
    assert response.status_code == 422
    assert any(error["loc"] == ["body", field] for error in response.json()["detail"])
    assert request_count(database_engine) == 0


@pytest.mark.parametrize(("field", "value"), [
    ("category", "MEDICINE"),
    ("category", "buy"),
    ("helper_reward", -1),
    ("helper_reward", "not a number"),
    ("helper_reward", "NaN"),
    ("helper_reward", "Infinity"),
    ("helper_reward", "12.345"),
    ("helper_reward", "100000000.00"),
    ("helper_reward", None),
    ("shopping_budget", -1),
    ("shopping_budget", "12.345"),
    ("shopping_budget", "100000000.00"),
    ("title", "   "),
    ("title", "x" * 201),
    ("description", "\n\t"),
    ("pickup_address", ""),
    ("delivery_address", "x" * 501),
    ("customer_id", 0),
    ("customer_id", -1),
    ("customer_id", True),
    ("customer_id", 2147483648),
    ("deadline", "invalid date"),
    ("deadline", "2000-01-01T12:00:00+01:00"),
])
def test_invalid_input_rejected_without_saving(
    client: TestClient, database_engine: Engine, payload: dict[str, Any],
    field: str, value: Any,
) -> None:
    payload[field] = value
    response = client.post("/requests", json=payload)
    assert response.status_code == 422
    assert any(error["loc"] == ["body", field] for error in response.json()["detail"])
    assert request_count(database_engine) == 0


@pytest.mark.parametrize(("field", "value"), [
    ("status", "DELIVERED"), ("helper_id", 2), ("id", 99),
])
def test_server_owned_fields_rejected(
    client: TestClient, database_engine: Engine, payload: dict[str, Any],
    field: str, value: Any,
) -> None:
    payload[field] = value
    assert client.post("/requests", json=payload).status_code == 422
    assert request_count(database_engine) == 0


def test_unknown_customer_returns_404(
    client: TestClient, database_engine: Engine, payload: dict[str, Any],
) -> None:
    payload["customer_id"] = 99999
    response = client.post("/requests", json=payload)
    assert response.status_code == 404
    assert response.json() == {"detail": "Customer not found."}
    assert request_count(database_engine) == 0


def test_optional_budget_zero_reward_and_trimmed_text(
    client: TestClient, payload: dict[str, Any],
) -> None:
    del payload["shopping_budget"]
    payload["helper_reward"] = 0
    payload["title"] = "  Buy groceries  "
    response = client.post("/requests", json=payload)
    assert response.status_code == 201
    assert response.json()["shopping_budget"] is None
    assert Decimal(response.json()["helper_reward"]) == 0
    assert response.json()["title"] == "Buy groceries"


def test_naive_deadline_uses_oslo_time(client: TestClient, payload: dict[str, Any]) -> None:
    oslo_time = (datetime.now(ZoneInfo("Europe/Oslo")) + timedelta(days=2)).replace(
        hour=18, minute=0, second=0, microsecond=0,
    )
    payload["deadline"] = oslo_time.replace(tzinfo=None).isoformat()
    response = client.post("/requests", json=payload)
    assert response.status_code == 201
    assert datetime.fromisoformat(response.json()["deadline"]) == oslo_time


@pytest.mark.parametrize(("error_type", "expected_status"), [
    (IntegrityError, 409), (OperationalError, 503),
])
def test_database_errors_roll_back_and_return_safe_message(
    client: TestClient, database_engine: Engine, payload: dict[str, Any],
    monkeypatch: pytest.MonkeyPatch, error_type: type[Exception], expected_status: int,
) -> None:
    original_flush = Session.flush

    def failing_flush(self: Session, objects: Any = None) -> None:
        original_flush(self, objects)
        if any(isinstance(obj, DeliveryRequest) for obj in self.identity_map.values()):
            raise error_type("INSERT", {}, Exception("private database details"))

    with monkeypatch.context() as patch:
        patch.setattr(Session, "flush", failing_flush)
        response = client.post("/requests", json=payload)

    assert response.status_code == expected_status
    assert "private database details" not in response.text
    assert request_count(database_engine) == 0
