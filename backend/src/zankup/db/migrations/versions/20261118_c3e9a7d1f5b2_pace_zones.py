"""Time in each pace zone per activity (runs and walks), for Progress → Training zones.

Revision ID: c3e9a7d1f5b2
Revises: b8d2f5a1c3e7
Create Date: 2026-11-18 01:00:00+00:00
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "c3e9a7d1f5b2"
down_revision: str | None = "b8d2f5a1c3e7"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "activity_metrics",
        sa.Column(
            "pace_zones",
            postgresql.JSONB(astext_type=sa.Text()),
            nullable=True,
            comment="Seconds per pace zone (runs and walks)",
        ),
    )


def downgrade() -> None:
    op.drop_column("activity_metrics", "pace_zones")
