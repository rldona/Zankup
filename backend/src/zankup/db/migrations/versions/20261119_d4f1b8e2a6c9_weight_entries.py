"""Weighings over time, for Health → Weight: one a day, the latest is the profile's weight.

Revision ID: d4f1b8e2a6c9
Revises: c3e9a7d1f5b2
Create Date: 2026-11-19 01:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "d4f1b8e2a6c9"
down_revision: str | None = "c3e9a7d1f5b2"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "weight_entries",
        sa.Column("id", sa.BigInteger(), sa.Identity(always=False), nullable=False),
        sa.Column("user_id", sa.BigInteger(), nullable=False),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("weight_kg", sa.Float(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_weight_entries_user_id_users"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_weight_entries")),
        sa.UniqueConstraint("user_id", "day", name=op.f("uq_weight_entries_user_id_day")),
    )
    op.create_index(op.f("ix_weight_entries_user_id"), "weight_entries", ["user_id"])
    # The profile's weight becomes the first weighing, dated the day it was last changed.
    op.execute(
        "INSERT INTO weight_entries (user_id, day, weight_kg) "
        "SELECT id, updated_at::date, weight_kg FROM users WHERE weight_kg IS NOT NULL"
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_weight_entries_user_id"), table_name="weight_entries")
    op.drop_table("weight_entries")
