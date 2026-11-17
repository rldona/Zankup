/**
 * Training log (the app's Log and training-log, as Strava's). The card: this week and the last as a row
 * of dots each (bigger, longer; coloured by sport, grey when a day has several) with their time. The
 * page: every week from this one back to the first activity, a bubble per day sized and labelled by
 * time, distance or elevation, with the filters (sport and what the bubbles show) above it, a list of
 * months to jump to and the search. Weeks load eight at a time as they come near the screen; a bubble
 * opens its activity, or lists the day's when it holds several.
 */
import {
  addDays,
  bubbleLabel,
  bubbleSize,
  calendarQuery,
  formatHoursMinutes,
  formatters,
  indexOfWeek,
  isoDay,
  monthsBack,
  SPORTS,
  streakQuery,
  weekAt,
  weekCount,
  weekDays,
  weekRange,
  type CalendarDay,
  type LogMetric,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router';

import { api } from '../../api';
import { useInView } from '../../charts/inView';
import { Modal } from '../../detail/Tools';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { usePrefs } from '../../prefs';
import { Select } from '../../Select';
import { CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle } from '../../ui';
import { asSport, dayColour, dayLetters, sportColour, total, type Activities } from './format';

/** Weeks fetched together, so scrolling asks the server once every eight weeks. */
const CHUNK = 8;

const weekId = (monday: string) => `week-${monday}`;

export function LogCard() {
  const [today] = useState(() => isoDay(new Date()));
  const from = weekAt(1, today);
  const calendar = useQuery(calendarQuery(api, { from, to: addDays(weekAt(0, today), 7) }));
  if (calendar.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={4} />
      </Pulse>
    );
  const days = new Map(((calendar.data ?? []) as CalendarDay[]).map((d) => [d.day, d.activities]));
  const week = (monday: string) => weekDays(monday).map((d) => ({ day: d, activities: days.get(d) ?? [] }));
  const thisWeek = week(weekAt(0, today));
  const lastWeek = week(from);
  const time = (w: typeof thisWeek) =>
    formatHoursMinutes(w.reduce((s, d) => s + total(d.activities, 'time'), 0));
  const line = (label: string, value: string) => (
    <span className="vline">
      <span>{label}</span>
      <span className="num">{value}</span>
    </span>
  );
  return (
    <Link
      to="/progress/log"
      className="panel vlogcard tap"
      aria-label={`${t('progress.log')}: ${t('progress.thisWeek')} ${time(thisWeek)}`}
    >
      <CardTitle right={<MaterialIcon name="chevron_right" />}>
        <MaterialIcon name="calendar_month" />
        {t('progress.log')}
      </CardTitle>
      {line(t('progress.thisWeek'), time(thisWeek))}
      <Dots days={thisWeek} today={today} />
      <Dots days={lastWeek} today={today} />
      {line(t('progress.lastWeek'), time(lastWeek))}
    </Link>
  );
}

/** A week as seven dots for the card: a speck on a rest day, a ring today if nothing yet. */
function Dots({ days, today }: { days: { day: string; activities: Activities }[]; today: string }) {
  return (
    <span className="vdots" aria-hidden="true">
      {days.map(({ day, activities }) => {
        const v = total(activities, 'time');
        const size = v ? Math.round(10 + 16 * Math.sqrt(Math.min(1, v / 7200))) : day === today ? 10 : 5;
        return (
          <span key={day}>
            <i
              className={v ? 'on' : day === today ? 'today' : ''}
              style={{ width: size, height: size, '--dot': dayColour(activities) } as CSSProperties}
            />
          </span>
        );
      })}
    </span>
  );
}

export function LogPage() {
  const [today] = useState(() => isoDay(new Date()));
  const streak = useQuery(streakQuery(api));
  const count = weekCount(streak.data?.first_activity, today);
  const [metric, setMetric] = useState<LogMetric>('time');
  const [sport, setSport] = useState<string | null>(null);
  const [months, setMonths] = useState(false);
  const [day, setDay] = useState<Activities | null>(null);
  const navigate = useNavigate();
  const chunks = Math.ceil(count / CHUNK);

  const open = (activities: Activities) => {
    const only = activities[0];
    if (activities.length === 1 && only) void navigate(`/activities/${only.id}`);
    else setDay(activities);
  };
  const jump = (iso: string) => {
    setMonths(false);
    const index = Math.min(count - 1, indexOfWeek(iso, today));
    document.getElementById(weekId(weekAt(index, today)))?.scrollIntoView({ block: 'start' });
  };

  return (
    <section className="page vlogpage">
      <div className="titlebar">
        <h1>{t('progress.log')}</h1>
        <button
          type="button"
          className="iconbtn"
          aria-label={t('progress.goToMonth')}
          title={t('progress.goToMonth')}
          onClick={() => setMonths(true)}
        >
          <MaterialIcon name="calendar_month" />
        </button>
        <Link className="iconbtn" to="/search" aria-label={t('nav.search')} title={t('nav.search')}>
          <MaterialIcon name="search" />
        </Link>
      </div>

      <div className="vfilters" role="group" aria-label={t('progress.filters')}>
        <div>
          <span>{t('progress.activityType')}</span>
          <Select
            label={t('progress.activityType')}
            value={sport ?? 'all'}
            onChange={(v) => setSport(v === 'all' ? null : v)}
            options={[
              { value: 'all', label: t('progress.all') },
              ...SPORTS.map((s) => ({ value: s, label: t(`sport.${s}`) })),
            ]}
          />
        </div>
        <div>
          <span>{t('progress.data')}</span>
          <Select
            label={t('progress.data')}
            value={metric}
            onChange={(v) => setMetric(v as LogMetric)}
            options={(['time', 'distance', 'ascent'] as const).map((m) => ({
              value: m,
              label: t(`progress.metric.${m}`),
            }))}
          />
        </div>
      </div>

      <div className="panel vlog">
        <div className="vletters" aria-hidden="true">
          {dayLetters(weekDays(weekAt(0, today))).map((l, i) => (
            <span key={i}>{l}</span>
          ))}
        </div>
        {streak.isPending ? (
          <Pulse>
            <CardSkeleton lines={6} />
          </Pulse>
        ) : (
          Array.from({ length: chunks }, (_, i) => (
            <Chunk key={i} index={i} count={count} today={today} metric={metric} sport={sport} onDay={open} />
          ))
        )}
      </div>

      {months && (
        <Modal title={t('progress.goToMonth')} className="vmonthsmodal" onClose={() => setMonths(false)}>
          <Months first={streak.data?.first_activity ?? today} today={today} onPick={jump} />
        </Modal>
      )}
      {day && <DayList activities={day} onClose={() => setDay(null)} />}
    </section>
  );
}

/** Eight weeks, fetched together once they come near the screen. */
function Chunk({
  index,
  count,
  today,
  metric,
  sport,
  onDay,
}: {
  index: number;
  count: number;
  today: string;
  metric: LogMetric;
  sport: string | null;
  onDay: (activities: Activities) => void;
}) {
  const [box, seen] = useInView<HTMLDivElement>('600px');
  const first = index * CHUNK;
  const weeks = Math.min(CHUNK, count - first);
  const from = weekAt(first + weeks - 1, today);
  const to = addDays(weekAt(first, today), 7);
  const calendar = useQuery({ ...calendarQuery(api, { from, to }), enabled: seen });
  const days = calendar.data
    ? new Map((calendar.data as CalendarDay[]).map((d) => [d.day, d.activities]))
    : null;
  return (
    <div ref={box}>
      {Array.from({ length: weeks }, (_, i) => (
        <WeekRow
          key={i}
          monday={weekAt(first + i, today)}
          days={days}
          metric={metric}
          sport={sport}
          today={today}
          onDay={onDay}
        />
      ))}
    </div>
  );
}

/** A week: its days and total, a bubble per day with activity, ▲ under today. */
function WeekRow({
  monday,
  days,
  metric,
  sport,
  today,
  onDay,
}: {
  monday: string;
  days: Map<string, Activities> | null;
  metric: LogMetric;
  sport: string | null;
  today: string;
  onDay: (activities: Activities) => void;
}) {
  const prefs = usePrefs();
  const format = useMemo(() => formatters(prefs), [prefs]);
  const imperial = prefs.units === 'imperial';
  const week = weekDays(monday).map((day) => ({
    day,
    activities: (days?.get(day) ?? []).filter((a) => !sport || asSport(a.sport) === sport),
  }));
  const sum = week.reduce((s, d) => s + total(d.activities, metric), 0);
  const label =
    metric === 'time'
      ? formatHoursMinutes(sum)
      : metric === 'distance'
        ? format.distance(sum)
        : format.elevation(sum);
  return (
    <div className="vrow" id={weekId(monday)} aria-busy={!days}>
      <div className="vrowhead">
        <span>{weekRange(monday, locale)}</span>
        <span className="num">{days ? label : ''}</span>
      </div>
      <div className="vbubbles">
        {week.map(({ day, activities }) => {
          const v = total(activities, metric);
          const size = bubbleSize(v, metric);
          const several = activities.length > 1;
          const text = bubbleLabel(v, metric, locale, imperial);
          const names = activities.map((a) => a.name ?? t(`sport.${asSport(a.sport)}`)).join(', ');
          return (
            <span key={day} className="vcell">
              {activities.length && size ? (
                <button
                  type="button"
                  className={`vbubble${several ? ' several' : ''}`}
                  aria-label={`${day}: ${text} · ${names}`}
                  title={names}
                  style={{ width: size, height: size, '--dot': dayColour(activities) } as CSSProperties}
                  onClick={() => onDay(activities)}
                >
                  <span className="num">{text}</span>
                  {several && <b>{activities.length}</b>}
                </button>
              ) : (
                <i className="vspeck" />
              )}
              {day === today && (
                <small className="vtoday" aria-hidden="true">
                  ▲
                </small>
              )}
            </span>
          );
        })}
      </div>
    </div>
  );
}

/** A day's activities, to choose one, when a bubble holds several. */
function DayList({ activities, onClose }: { activities: Activities; onClose: () => void }) {
  const prefs = usePrefs();
  const format = useMemo(() => formatters(prefs), [prefs]);
  return (
    <Modal title={t('progress.dayActivities', { count: activities.length })} onClose={onClose}>
      <ul className="vdaylist">
        {activities.map((a) => (
          <li key={a.id}>
            <Link to={`/activities/${a.id}`}>
              <i style={{ '--dot': sportColour(a.sport) } as CSSProperties} />
              <span>{a.name ?? t(`sport.${asSport(a.sport)}`)}</span>
              <small className="num">
                {[a.distance_m ? format.distance(a.distance_m) : null, formatHoursMinutes(a.moving_s)]
                  .filter(Boolean)
                  .join(' · ')}
              </small>
            </Link>
          </li>
        ))}
      </ul>
    </Modal>
  );
}

/** Every month from this one back to the first activity's, under its year; a click goes to its first week. */
function Months({ first, today, onPick }: { first: string; today: string; onPick: (iso: string) => void }) {
  const months = monthsBack(first, today);
  const years = [...new Set(months.map((m) => m.year))];
  return (
    <div className="vmonths">
      {years.map((year) => (
        <section key={year}>
          <h3>{year}</h3>
          <div>
            {months
              .filter((m) => m.year === year)
              .map(({ month }) => {
                const iso = `${year}-${String(month).padStart(2, '0')}-01`;
                return (
                  <button key={iso} type="button" onClick={() => onPick(iso < first ? first : iso)}>
                    {new Date(`${iso}T12:00:00`).toLocaleDateString(locale, { month: 'long' })}
                  </button>
                );
              })}
          </div>
        </section>
      ))}
    </div>
  );
}
