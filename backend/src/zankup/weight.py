"""Weighings (Health → Weight): one a day, and the latest is the profile's weight, which the metrics
engine uses for effort and calories, so a change there recomputes."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from zankup.db.models import User, WeightEntry


def save(db: Session, user_id: int, day: date, weight_kg: float) -> WeightEntry:
    """The day's weighing: a new one, or the one already there changed."""
    entry = db.scalar(select(WeightEntry).where(WeightEntry.user_id == user_id, WeightEntry.day == day))
    if entry is None:
        entry = WeightEntry(user_id=user_id, day=day, weight_kg=weight_kg)
        db.add(entry)
    else:
        entry.weight_kg = weight_kg
    db.flush()
    return entry


def follow_latest(db: Session, user: User) -> None:
    """The profile takes the latest weighing; with none left it keeps what it had. Recomputes on a change."""
    latest = db.scalar(
        select(WeightEntry.weight_kg)
        .where(WeightEntry.user_id == user.id)
        .order_by(WeightEntry.day.desc())
        .limit(1)
    )
    if latest is None or latest == user.weight_kg:
        return
    user.weight_kg = latest
    from zankup.engine.run import queue_full

    queue_full(db, user.id, "weight changed")  # effort and calories use it
