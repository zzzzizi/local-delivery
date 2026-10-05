from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    Enum,
    ForeignKey,
    Numeric,
    String,
    Text,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.enums import RequestCategory, RequestStatus

if TYPE_CHECKING:
    from app.models.user import User


class DeliveryRequest(Base):
    __tablename__ = "delivery_requests"
    __table_args__ = (
        CheckConstraint("helper_reward >= 0", name="ck_requests_helper_reward"),
        CheckConstraint("shopping_budget >= 0", name="ck_requests_shopping_budget"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    customer_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    helper_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), index=True)
    category: Mapped[RequestCategory] = mapped_column(
        Enum(RequestCategory, name="request_category")
    )
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text)
    pickup_address: Mapped[str] = mapped_column(String(500))
    delivery_address: Mapped[str] = mapped_column(String(500))
    shopping_budget: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    helper_reward: Mapped[Decimal] = mapped_column(Numeric(10, 2))
    deadline: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    status: Mapped[RequestStatus] = mapped_column(
        Enum(RequestStatus, name="request_status"),
        server_default=RequestStatus.OPEN.value,
        index=True,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    customer: Mapped[User] = relationship(
        back_populates="requests_created", foreign_keys=[customer_id]
    )
    helper: Mapped[User | None] = relationship(
        back_populates="deliveries_accepted", foreign_keys=[helper_id]
    )
