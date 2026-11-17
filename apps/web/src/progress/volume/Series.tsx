/**
 * Series on Progress (the app's Series, as Strava's Streaks): weeks in a row with at least one activity,
 * in a flame. A click opens the month in a dialog: each day with its sport (a click opens the activity)
 * and, beside each week, whether it kept the streak, on a band that runs through the streak to this
 * week's flame.
 */
import {
  addDays,
  calendarQuery,
  isoDay,
  mondayOf,
  SPORT_ICONS,
  streakQuery,
  weekDays,
  weekMark,
  type CalendarDay,
  type WeekMark,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useState, type PointerEvent } from 'react';
import { Link } from 'react-router';

import { api } from '../../api';
import { Modal } from '../../detail/Tools';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle, InfoTip } from '../../ui';
import { asSport, dayLetters, type Activities } from './format';

export function SeriesCard() {
  const streak = useQuery(streakQuery(api));
  const [open, setOpen] = useState(false);
  const [ripple, setRipple] = useState<Ripple | null>(null);
  if (streak.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={4} />
      </Pulse>
    );
  const weeks = streak.data?.weeks ?? 0;
  return (
    <>
      <article className="panel vseries" onPointerDown={(e) => setRipple(rippleAt(e))}>
        {/* The title's button covers the whole card (a heading cannot sit inside a button). */}
        <CardTitle right={<MaterialIcon name="chevron_right" />}>
          <button
            type="button"
            className="vstretch"
            aria-haspopup="dialog"
            aria-label={`${t('progress.series')}: ${t('progress.streakWeeks', { count: weeks })}`}
            onClick={() => setOpen(true)}
          >
            {t('progress.series')}
          </button>
        </CardTitle>
        <span className="vflamebox">
          <Flame weeks={weeks} size={96} />
          <b>{t('progress.weeksWord', { count: weeks })}</b>
        </span>
        {ripple && (
          <span
            key={ripple.key}
            className="vripple"
            style={{ left: ripple.x, top: ripple.y, width: ripple.size, height: ripple.size }}
            onAnimationEnd={() => setRipple(null)}
            aria-hidden="true"
          />
        )}
      </article>
      {open && (
        <Modal title={t('progress.series')} className="vmonthmodal" onClose={() => setOpen(false)}>
          <SeriesMonth />
        </Modal>
      )}
    </>
  );
}

interface Ripple {
  x: number;
  y: number;
  size: number;
  key: number;
}

/** Android's press ripple: a circle that grows from where the card was pressed until it covers it. */
function rippleAt(e: PointerEvent<HTMLElement>): Ripple {
  const box = e.currentTarget.getBoundingClientRect();
  const x = e.clientX - box.left;
  const y = e.clientY - box.top;
  const reach = Math.hypot(Math.max(x, box.width - x), Math.max(y, box.height - y));
  return { x, y, size: reach * 2, key: e.timeStamp };
}

/**
 * A solid flame (two tips, the left one taller, as Strava's) in a 100-unit box, so the weeks can sit on
 * it: Material's filled flame has a hollow inside.
 */
const FLAME =
  'M50 99C24 99 9 82 9 61C9 41 22 30 30 9C35 23 42 31 47 35C50 25 55 19 58 10C74 27 91 42 91 63C91 84 75 99 50 99Z';

/** The flame with the weeks on it, low down where it is widest. */
function Flame({ weeks, size }: { weeks: number; size: number }) {
  return (
    <span className="vflame" style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox="0 0 100 100" width={size} height={size}>
        <path d={FLAME} />
      </svg>
      <b className="num" style={{ fontSize: size * (weeks > 99 ? 0.26 : 0.32) }}>
        {weeks}
      </b>
    </span>
  );
}

function SeriesMonth() {
  const [now] = useState(() => new Date());
  const today = isoDay(now);
  const [month, setMonth] = useState({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const firstDay = isoDay(new Date(month.year, month.month - 1, 1, 12));
  const lastDay = addDays(isoDay(new Date(month.year, month.month, 1, 12)), -1);
  // The Monday-to-Sunday weeks that cover the month.
  const from = mondayOf(firstDay);
  const to = addDays(mondayOf(lastDay), 7);
  const calendar = useQuery(calendarQuery(api, { from, to }));
  const streak = useQuery(streakQuery(api)).data;
  const days = new Map(((calendar.data ?? []) as CalendarDay[]).map((d) => [d.day, d.activities]));
  const mondays: string[] = [];
  for (let d = from; d < to; d = addDays(d, 7)) mondays.push(d);
  const marks = mondays.map((m) =>
    weekMark(
      m,
      today,
      weekDays(m).some((d) => days.get(d)?.length),
      { since: streak?.since ?? null, weeks: streak?.weeks ?? 0 },
    ),
  );
  const title = new Date(month.year, month.month - 1, 15).toLocaleDateString(locale, {
    month: 'long',
    year: 'numeric',
  });
  const isThisMonth = month.year === now.getFullYear() && month.month === now.getMonth() + 1;
  const shift = (by: number) => {
    const d = new Date(month.year, month.month - 1 + by, 15);
    setMonth({ year: d.getFullYear(), month: d.getMonth() + 1 });
  };
  const band = (m: WeekMark | undefined) => m === 'streak' || m === 'current';

  return (
    <div className="vmonth">
      <div className="vmonthhead">
        <h3>{title}</h3>
        <button
          type="button"
          className="iconbtn"
          aria-label={t('progress.prevMonth')}
          title={t('progress.prevMonth')}
          onClick={() => shift(-1)}
        >
          <MaterialIcon name="chevron_left" />
        </button>
        <button
          type="button"
          className="iconbtn"
          aria-label={t('progress.nextMonth')}
          title={t('progress.nextMonth')}
          disabled={isThisMonth}
          onClick={() => shift(1)}
        >
          <MaterialIcon name="chevron_right" />
        </button>
        <InfoTip text={t('progress.streakInfo')} />
      </div>
      <dl className="vfigs">
        <div>
          <dt>{t('progress.yourStreak')}</dt>
          <dd>{t('progress.streakWeeks', { count: streak?.weeks ?? 0 })}</dd>
        </div>
        <div>
          <dt>{t('progress.streakActivities')}</dt>
          <dd className="num">{streak?.activities ?? 0}</dd>
        </div>
      </dl>
      {!streak?.weeks && <p className="note">{t('progress.noStreak')}</p>}

      <div className="vmonthgrid" role="table" aria-label={title} aria-busy={calendar.isPending}>
        <div className="vweek head" role="row">
          {dayLetters(weekDays(mondays[0] ?? from)).map((l, i) => (
            <span key={i} role="columnheader">
              {l}
            </span>
          ))}
          <span role="columnheader" />
        </div>
        {mondays.map((monday, w) => {
          const mark = marks[w] ?? 'future';
          const joinUp = band(mark) && band(marks[w - 1]);
          const joinDown = band(mark) && band(marks[w + 1]);
          return (
            <div key={monday} className="vweek" role="row">
              {weekDays(monday).map((day) => (
                <span key={day} role="cell">
                  <DayCell
                    day={day}
                    today={today}
                    inMonth={Number(day.slice(5, 7)) === month.month}
                    activities={days.get(day) ?? []}
                  />
                </span>
              ))}
              <span role="cell" className="vmark">
                {band(mark) && <i className={`vband${joinUp ? ' up' : ''}${joinDown ? ' down' : ''}`} />}
                <WeekBadge mark={mark} weeks={streak?.weeks ?? 0} />
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function WeekBadge({ mark, weeks }: { mark: WeekMark; weeks: number }) {
  if (mark === 'current') return <Flame weeks={weeks} size={44} />;
  if (mark === 'streak' || mark === 'done')
    return (
      <span className="vtick">
        <MaterialIcon name="check" />
      </span>
    );
  return <span className="vtick empty" />;
}

function DayCell({
  day,
  today,
  inMonth,
  activities,
}: {
  day: string;
  today: string;
  inMonth: boolean;
  activities: Activities;
}) {
  const n = Number(day.slice(8));
  const first = activities[0];
  if (!inMonth) return <span className="vday out">{n}</span>;
  if (first) {
    const names = activities.map((a) => a.name ?? t(`sport.${asSport(a.sport)}`)).join(', ');
    return (
      <Link
        to={`/activities/${first.id}`}
        className={`vday done${day === today ? ' today' : ''}`}
        aria-label={`${n}: ${names}`}
        title={names}
      >
        <MaterialIcon name={SPORT_ICONS[asSport(first.sport)]} />
        {activities.length > 1 && <i className="vplus" aria-hidden="true" />}
      </Link>
    );
  }
  return (
    <span className={`vday${day < today ? ' past' : day === today ? ' today' : ' ahead'}`}>
      <span className="num">{n}</span>
    </span>
  );
}
