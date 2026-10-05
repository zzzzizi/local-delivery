from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING

from sqlalchemy import CheckConstraint, DateTime, Numeric, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.delivery_request import DeliveryRequest


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        CheckConstraint("rating >= 0 AND rating <= 5", name="ck_users_rating"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(String(320), unique=True)
    phone: Mapped[str | None] = mapped_column(String(32))
    rating: Mapped[Decimal | None] = mapped_column(Numeric(3, 2))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    requests_created: Mapped[list[DeliveryRequest]] = relationship(
        back_populates="customer", foreign_keys="DeliveryRequest.customer_id"
    )
    deliveries_accepted: Mapped[list[DeliveryRequest]] = relationship(
        back_populates="helper", foreign_keys="DeliveryRequest.helper_id"
    )
