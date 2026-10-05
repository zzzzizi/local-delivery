from typing import Literal

from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
def get_health() -> dict[str, Literal["ok"]]:
    """Report that the API is running; no database is needed in Stage 1."""
    return {"status": "ok"}
