from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.enums import RequestCategory, RequestStatus

Money = Annotated[Decimal, Field(ge=0, max_digits=10, decimal_places=2)]


class DeliveryRequestCreate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    customer_id: int = Field(gt=0, le=2147483647, strict=True)
    category: RequestCategory
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(min_length=1)
    pickup_address: str = Field(min_length=1, max_length=500)
    delivery_address: str = Field(min_length=1, max_length=500)
    shopping_budget: Money | None = None
    helper_reward: Money
    deadline: datetime = Field(
        description="A future deadline. Include a UTC offset; otherwise Oslo time is used."
    )

    @field_validator("deadline")
    @classmethod
    def validate_deadline(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            value = value.replace(tzinfo=ZoneInfo("Europe/Oslo"))
        value = value.astimezone(timezone.utc)
        if value <= datetime.now(timezone.utc):
            raise ValueError("Deadline must be in the future.")
        return value


class DeliveryRequestRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    customer_id: int
    helper_id: int | None
    category: RequestCategory
    title: str
    description: str
    pickup_address: str
    delivery_address: str
    shopping_budget: Money | None = Field(examples=["300.00", None])
    helper_reward: Money = Field(examples=["80.00"])
    deadline: datetime
    status: RequestStatus
    created_at: datetime
    updated_at: datetime
