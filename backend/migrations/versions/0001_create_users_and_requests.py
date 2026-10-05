"""Create users and delivery requests.

Revision ID: 0001
Revises: None
"""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

category = sa.Enum("PACKAGE", "BUY", "PICKUP", name="request_category")
status = sa.Enum(
    "OPEN", "ACCEPTED", "PICKED_UP", "DELIVERING", "DELIVERED", "CANCELLED",
    name="request_status",
)


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("email", sa.String(320), nullable=False),
        sa.Column("phone", sa.String(32), nullable=True),
        sa.Column("rating", sa.Numeric(3, 2), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("email"),
        sa.CheckConstraint("rating >= 0 AND rating <= 5", name="ck_users_rating"),
    )
    op.create_table(
        "delivery_requests",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("customer_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("helper_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("category", category, nullable=False),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column("pickup_address", sa.String(500), nullable=False),
        sa.Column("delivery_address", sa.String(500), nullable=False),
        sa.Column("shopping_budget", sa.Numeric(10, 2), nullable=True),
        sa.Column("helper_reward", sa.Numeric(10, 2), nullable=False),
        sa.Column("deadline", sa.DateTime(timezone=True), nullable=False),
        sa.Column("status", status, server_default="OPEN", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("helper_reward >= 0", name="ck_requests_helper_reward"),
        sa.CheckConstraint("shopping_budget >= 0", name="ck_requests_shopping_budget"),
    )
    op.create_index("ix_delivery_requests_customer_id", "delivery_requests", ["customer_id"])
    op.create_index("ix_delivery_requests_helper_id", "delivery_requests", ["helper_id"])
    op.create_index("ix_delivery_requests_status", "delivery_requests", ["status"])


def downgrade() -> None:
    op.drop_table("delivery_requests")
    op.drop_table("users")
    status.drop(op.get_bind(), checkfirst=True)
    category.drop(op.get_bind(), checkfirst=True)
