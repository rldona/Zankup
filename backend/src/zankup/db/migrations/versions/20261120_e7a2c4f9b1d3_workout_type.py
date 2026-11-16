"""A strength workout's kind (rowing, hyrox, grit…), chosen by the user, for its muscles.

Revision ID: e7a2c4f9b1d3
Revises: d4f1b8e2a6c9
Create Date: 2026-11-20 01:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "e7a2c4f9b1d3"
down_revision: str | None = "d4f1b8e2a6c9"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "activities",
        sa.Column(
            "workout_type",
            sa.String(length=20),
            nullable=True,
            comment="A strength workout's kind, chosen by the user (rowing, hyrox, grit…): its muscles",
        ),
    )


def downgrade() -> None:
    op.drop_column("activities", "workout_type")
