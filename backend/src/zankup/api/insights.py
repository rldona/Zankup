"""What an activity's page shows around its own numbers (as Strava's): how the day's training moved
fitness, its relative effort against the weeks before, the goals it counts for, the earlier efforts on
the same route, its results (medals in best times and power, and on segments) and, on a ride with
power, its work, intensity and training load.

One request for all of them, so the page asks once: GET /api/activities/{id}/context.
"""

from __future__ import annotations

from datetime import date, timedelta
from typing import Literal

from pydantic import BaseModel, Field
from sqlalchemy import select

import zankup_metrics as m
from zankup.api.routing import protected_router
from zankup.api.stats import BEST_KEYS, _bests, lower_is_better, medal_of
from zankup.api.training import ReachedGoal, reached_goals
from zankup.auth.deps import CurrentUser, DbSession
from zankup.db.models import (
    Activity,
    ActivityMetrics,
    DailyLoad,
    EstimateRow,
    Goal,
    Segment,
    SegmentEffort,
)
from zankup.engine.athlete import athlete
from zankup.engine.user import local_day

router = protected_router(prefix="/api/activities", tags=["activities"])


class FitnessPoint(BaseModel):
    day: date
    ctl: float


class FitnessOut(BaseModel):
    before: float = Field(description="Fitness the day before")
    after: float = Field(description="Fitness at the end of the activity's day")
    points: list[FitnessPoint] = Field(description="The four weeks up to the day, for a small line")


class EffortOut(BaseModel):
    score: float
    average: float | None = Field(description="Average relative effort per activity in the 3 weeks before")
    status: Literal["below", "in", "above"] | None


class GoalOut(BaseModel):
    period: str
    metric: str
    sport: str | None
    target: float
    current: float


class MatchOut(BaseModel):
    activity_id: int
    day: date
    speed_mps: float | None
    moving_s: float
    relative_effort: float | None
    this: bool = Field(description="The activity the page is about")


class Medal(BaseModel):
    key: str = Field(description="A run distance, power_30… or longest")
    value: float
    rank: int
    scope: Literal["all", "year"]


class SegmentResult(BaseModel):
    segment_id: int
    name: str
    distance_m: float
    elapsed_s: float
    avg_power: int | None
    rank: int = Field(description="Among every effort on the segment")


class PowerOut(BaseModel):
    work_kj: float
    intensity: float | None = Field(description="Normalized power over FTP, as a percentage")
    load: float | None = Field(description="Training load: time * NP * IF / (FTP * 3600) * 100")
    ftp: float | None


class PaceZonesOut(BaseModel):
    threshold_mps: float = Field(description="Threshold speed: the engine's critical speed estimate")
    bounds: list[float] = Field(description="Each zone's lower bound as a speed (m/s), Z1 from 0")


class ActivityContext(BaseModel):
    fitness: FitnessOut | None
    effort: EffortOut | None
    goals: list[GoalOut]
    matches: list[MatchOut] = Field(description="Every effort on the same route, oldest first")
    medals: list[Medal]
    segments: list[SegmentResult]
    power: PowerOut | None
    pace_zones: PaceZonesOut | None = Field(description="On a run, the zones its pace is read against")
    goals_reached: list[ReachedGoal] = Field(
        default_factory=list, description="The goals this activity completed (took over the target)"
    )


def _fitness(db: DbSession, user_id: int, day: date) -> FitnessOut | None:
    rows = list(
        db.scalars(
            select(DailyLoad)
            .where(
                DailyLoad.user_id == user_id, DailyLoad.day > day - timedelta(days=29), DailyLoad.day <= day
            )
            .order_by(DailyLoad.day)
        )
    )
    if not rows or rows[-1].day != day:
        return None
    before = rows[-2].ctl if len(rows) > 1 else 0.0
    return FitnessOut(
        before=round(before, 1),
        after=round(rows[-1].ctl, 1),
        points=[FitnessPoint(day=r.day, ctl=round(r.ctl, 1)) for r in rows],
    )


def effort_status(score: float, average: float | None) -> Literal["below", "in", "above"] | None:
    """Against the average of the weeks before: within two thirds to four thirds of it is "in"."""
    if not average:
        return None
    if score < average * 2 / 3:
        return "below"
    return "in" if score <= average * 4 / 3 else "above"


def _effort(db: DbSession, activity: Activity, score: float | None) -> EffortOut | None:
    if score is None:
        return None
    rows = db.execute(
        select(ActivityMetrics.trimp)
        .join(Activity, Activity.id == ActivityMetrics.activity_id)
        .where(
            Activity.user_id == activity.user_id,
            Activity.start_utc < activity.start_utc,
            Activity.start_utc >= activity.start_utc - timedelta(days=21),
            ActivityMetrics.trimp.is_not(None),
        )
    ).all()
    values = [r[0] for r in rows if r[0]]
    average = round(sum(values) / len(values), 1) if values else None
    return EffortOut(score=round(score, 1), average=average, status=effort_status(score, average))


def _goals(db: DbSession, activity: Activity, day: date) -> list[GoalOut]:
    from zankup.api.training import _goal_out

    out = []
    for goal in db.scalars(select(Goal).where(Goal.user_id == activity.user_id).order_by(Goal.id)):
        if goal.sport and goal.sport != activity.sport:
            continue
        g = _goal_out(db, goal, day)
        out.append(
            GoalOut(period=g.period, metric=g.metric, sport=g.sport, target=g.target, current=g.current)
        )
    return out


def _matches(db: DbSession, activity: Activity, group: int | None) -> list[MatchOut]:
    if group is None:
        return []
    rows = db.execute(
        select(Activity, ActivityMetrics)
        .join(ActivityMetrics, ActivityMetrics.activity_id == Activity.id)
        .where(ActivityMetrics.user_id == activity.user_id, ActivityMetrics.route_group == group)
        .order_by(Activity.start_utc)
    ).all()
    if len(rows) < 2:
        return []
    out = []
    for a, mt in rows:
        moving = a.moving_s or a.duration_s
        out.append(
            MatchOut(
                activity_id=a.id,
                day=local_day(a.start_utc, a.timezone),
                speed_mps=round(a.distance_m / moving, 3) if a.distance_m and moving else None,
                moving_s=moving,
                relative_effort=round(mt.trimp, 1) if mt.trimp else None,
                this=a.id == activity.id,
            )
        )
    return out


def _medals(db: DbSession, activity: Activity) -> list[Medal]:
    """This activity's places in the bests: top three of all time, or else of its year."""
    keys = [
        k
        for k in BEST_KEYS
        if (activity.sport == "run" and not k.startswith("power_") and k != "longest")
        or (activity.sport == "ride" and (k.startswith("power_") or k == "longest"))
    ]
    out = []
    for key in keys:
        entries = _bests(db, activity.user_id, key)
        mine = next((e for e in entries if e.activity_id == activity.id), None)
        if mine is None:
            continue
        place = medal_of(entries, mine, lower_is_better(key))
        if place:
            out.append(Medal(key=key, value=mine.value, rank=place[0], scope=place[1]))
    out.sort(key=lambda m: (m.scope != "all", m.rank))
    return out


def _segments(db: DbSession, activity: Activity) -> list[SegmentResult]:
    out = []
    for effort, segment in db.execute(
        select(SegmentEffort, Segment)
        .join(Segment, Segment.id == SegmentEffort.segment_id)
        .where(SegmentEffort.activity_id == activity.id)
    ).all():
        times = list(
            db.scalars(select(SegmentEffort.elapsed_s).where(SegmentEffort.segment_id == segment.id))
        )
        out.append(
            SegmentResult(
                segment_id=segment.id,
                name=segment.name,
                distance_m=segment.distance_m,
                elapsed_s=effort.elapsed_s,
                avg_power=effort.avg_power,
                rank=1 + sum(1 for t in times if t < effort.elapsed_s),
            )
        )
    return sorted(out, key=lambda s: (s.rank, s.name))


def _pace_zones(db: DbSession, user_id: int) -> PaceZonesOut | None:
    """Pace zones over the threshold speed (zankup_metrics' five), from the latest critical speed."""
    threshold = db.scalar(
        select(EstimateRow.value)
        .where(EstimateRow.user_id == user_id, EstimateRow.kind == "critical_speed")
        .order_by(EstimateRow.day.desc())
        .limit(1)
    )
    if not threshold:
        return None
    return PaceZonesOut(
        threshold_mps=round(threshold, 3), bounds=[round(b, 3) for b in m.pace_zones(threshold)]
    )


def power_figures(
    avg_power: float | None, normalized: float | None, moving_s: float, ftp: float | None
) -> PowerOut | None:
    """Work from the average power; intensity and load (as TrainingPeaks' TSS) need an FTP."""
    if not avg_power or moving_s <= 0:
        return None
    work = round(avg_power * moving_s / 1000)
    if not ftp or not normalized:
        return PowerOut(work_kj=work, intensity=None, load=None, ftp=ftp)
    factor = normalized / ftp
    return PowerOut(
        work_kj=work,
        intensity=round(factor * 100),
        load=round(moving_s * normalized * factor / (ftp * 3600) * 100),
        ftp=round(ftp),
    )


@router.get("/{activity_id}/context", operation_id="getActivityContext")
def activity_context(activity_id: int, db: DbSession, user: CurrentUser) -> ActivityContext:
    """Fitness, effort, goals, the same route, results and power figures around one activity."""
    from fastapi import HTTPException, status

    activity = db.get(Activity, activity_id)
    if activity is None or activity.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Activity not found")
    metrics = db.get(ActivityMetrics, activity.id)
    day = local_day(activity.start_utc, activity.timezone)
    who = athlete(db, user.id)
    return ActivityContext(
        fitness=_fitness(db, user.id, day),
        effort=_effort(db, activity, metrics.trimp if metrics else None),
        goals=_goals(db, activity, day),
        matches=_matches(db, activity, metrics.route_group if metrics else None),
        medals=_medals(db, activity),
        segments=_segments(db, activity),
        pace_zones=_pace_zones(db, user.id) if activity.sport == "run" else None,
        goals_reached=reached_goals(db, user.id, [activity]).get(activity.id, []),
        power=power_figures(
            activity.avg_power,
            activity.normalized_power,
            activity.moving_s or activity.duration_s,
            who.ftp,
        ),
    )
