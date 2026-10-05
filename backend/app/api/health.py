from typing import Literal

from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def get_health() -> dict[str, Literal["ok"]]:
    """Report API liveness without querying the database."""
    return {"status": "ok"}
