/**
 * Weight over time (OS-1009, as the app's #294 and #297): the card on Health after today's figures
 * (the latest weighing, how it moved in 30 days and the last 90 days as a line; with none yet, it asks
 * for the first) and its page, /health/weight: the line over a month to all time with the day under
 * the pointer read out, every weighing newest first with its step from the one before, and adding,
 * changing and deleting one in a dialog. The latest weighing is the profile's weight.
 */
import {
  commonTokens,
  dailyWeights,
  parseWeight,
  queryKeys,
  themes,
  weightChange,
  weightHistory,
  weightQuery,
  weightRangeStart,
  WEIGHT_RANGES,
  type ThemeName,
  type Weighing,
  type WeightRange,
} from '@zankup/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { EChartsType } from 'echarts/core';
import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { api } from '../api';
import { EChart } from '../charts/EChart';
import { Modal } from '../detail/Tools';
import { t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { CardSkeleton, ChartSkeleton, Pulse } from '../Skeleton';
import { useResolvedTheme } from '../theme';
import { CardTitle } from '../ui';
import { localToday } from '../week/today';
import { daysBetween, kg, longDay, shortDay, signed } from './weightFormat';
import './weight.css';

const palette = (theme: ThemeName) => ({ ...commonTokens, ...themes[theme] });

const back = (today: string, days: number) => {
  const d = new Date(`${today}T12:00:00`);
  d.setDate(d.getDate() - days);
  return localToday(d);
};

/** The weight's line. Bare on the card; on the page with its axes and the day under the pointer. */
function WeightLine({
  days,
  values,
  weighed,
  height,
  summary,
  bare = false,
  onPick,
}: {
  days: string[];
  values: (number | null)[];
  /** The days with a weighing of their own, marked with a dot on the page. */
  weighed?: Set<string>;
  height: number;
  summary: string;
  bare?: boolean;
  onPick?: (index: number | null) => void;
}) {
  const theme = useResolvedTheme();
  const option = useMemo(() => {
    const c = palette(theme);
    const shown = values.filter((v): v is number => v != null);
    const min = Math.floor(Math.min(...shown) - 1);
    const max = Math.ceil(Math.max(...shown) + 1);
    return {
      animationDurationUpdate: 500,
      grid: bare ? { left: 4, right: 4, top: 6, bottom: 6 } : { left: 44, right: 16, top: 16, bottom: 28 },
      tooltip: bare
        ? undefined
        : {
            trigger: 'axis',
            showContent: false,
            axisPointer: { type: 'line', lineStyle: { color: c.muted, width: 1 } },
          },
      xAxis: {
        type: 'category',
        show: !bare,
        boundaryGap: false,
        data: days,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { formatter: shortDay, hideOverlap: true, color: c.muted, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        show: !bare,
        min,
        max,
        axisLabel: { color: c.muted, fontSize: 11, formatter: kg },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          id: 'weight',
          type: 'line',
          data: values.map((v) => (v == null ? null : Math.round(v * 100) / 100)),
          symbol: (_: unknown, params: { dataIndex: number }) =>
            weighed?.has(days[params.dataIndex] ?? '') ? 'circle' : 'none',
          symbolSize: 6,
          silent: bare,
          lineStyle: { color: c.accent, width: bare ? 2.5 : 3, join: 'round', cap: 'round' },
          itemStyle: { color: c.accent },
          areaStyle: bare ? { color: c.accent, opacity: 0.12 } : undefined,
        },
      ],
    };
  }, [days, values, weighed, bare, theme]);
  const ready = useCallback(
    (chart: EChartsType) => {
      if (!onPick) return undefined;
      const point = (event: unknown) =>
        onPick((event as { axesInfo?: { value: number }[] }).axesInfo?.[0]?.value ?? null);
      const out = () => onPick(null);
      chart.on('updateAxisPointer', point);
      chart.getZr().on('globalout', out);
      return () => {
        chart.off('updateAxisPointer', point);
        chart.getZr().off('globalout', out);
      };
    },
    [onPick],
  );
  return (
    <EChart
      option={option}
      theme={theme}
      height={height}
      summary={summary}
      onReady={onPick ? ready : undefined}
      className={bare ? 'wspark' : 'wchart'}
    />
  );
}

/** Health's Weight card: the latest weighing, its change in 30 days and the last 90 days; it opens the page. */
export function WeightCard() {
  const weight = useQuery(weightQuery(api));
  const [today] = useState(localToday);
  const data = weight.data;
  const line = useMemo(() => {
    if (!data) return null;
    const from = back(today, 90);
    return { days: daysBetween(from, today), values: dailyWeights(data, from, today) };
  }, [data, today]);
  if (weight.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={2} />
      </Pulse>
    );
  if (!data || !line) return null;
  const latest = data.at(-1);
  const moved = weightChange(data, back(today, 30));
  const shown = line.values.filter((v) => v != null).length;
  return (
    <Link
      to="/health/weight"
      className="panel weightcard"
      aria-label={latest ? `${t('weight.title')}, ${kg(latest.weight_kg)} kg` : t('weight.title')}
      title={t('weight.open')}
    >
      <CardTitle right={<MaterialIcon name="chevron_right" />}>
        <MaterialIcon name="weight" />
        {t('weight.title')}
      </CardTitle>
      {latest ? (
        <>
          <p className="wfigure">
            <b className="num">{kg(latest.weight_kg)} kg</b>
            {moved != null && <span className="note">{t('weight.change30', { value: signed(moved) })}</span>}
          </p>
          {shown > 1 && (
            <WeightLine
              days={line.days}
              values={line.values}
              height={72}
              bare
              summary={`${t('weight.title')} ${kg(latest.weight_kg)} kg`}
            />
          )}
        </>
      ) : (
        <p className="note">{t('weight.empty')}</p>
      )}
    </Link>
  );
}

type Editing = { day: string; weight: string; old?: string };

/** /health/weight: the line over the range chosen, the history, and adding, changing or deleting a weighing. */
export function WeightPage() {
  const weight = useQuery(weightQuery(api));
  const client = useQueryClient();
  const [today] = useState(localToday);
  const [range, setRange] = useState<WeightRange>('3m');
  const [editing, setEditing] = useState<Editing | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);
  const [picked, setPicked] = useState<number | null>(null);

  const weighings = useMemo(() => weight.data ?? [], [weight.data]);
  const from = weightRangeStart(range, today, weighings);
  const days = useMemo(() => daysBetween(from, today), [from, today]);
  const values = useMemo(() => dailyWeights(weighings, from, today), [weighings, from, today]);
  const weighed = useMemo(() => new Set(weighings.map((w) => w.day)), [weighings]);
  const history = useMemo(() => weightHistory(weighings), [weighings]);
  const latest = weighings.at(-1);
  const moved = weightChange(weighings, from);
  const shown = values.filter((v) => v != null).length;

  const changed = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: weightQuery(api).queryKey }),
      client.invalidateQueries({ queryKey: queryKeys.me }),
    ]);
  const remove = useMutation({
    mutationFn: async (day: string) => {
      const { error } = await api.DELETE('/api/weight/{day}', { params: { path: { day } } });
      if (error) throw new Error('delete');
    },
    onSuccess: async () => {
      setDeleting(null);
      await changed();
    },
  });

  const add = () => setEditing({ day: today, weight: latest ? kg(latest.weight_kg) : '' });
  const pickedDay = picked != null ? days[picked] : undefined;
  const pickedValue = picked != null ? values[picked] : undefined;

  return (
    <section className="page health weightpage">
      <div className="titlebar">
        <h1>{t('weight.title')}</h1>
        <button type="button" className="primary" onClick={add}>
          <MaterialIcon name="add" />
          {t('weight.add')}
        </button>
      </div>

      {weight.isPending && (
        <Pulse>
          <ChartSkeleton height={280} />
        </Pulse>
      )}
      {weight.isError && (
        <p className="warn" role="alert">
          {t('common.error')}
        </p>
      )}
      {weight.isSuccess && !latest && (
        <div className="panel empty">
          <p>{t('weight.empty')}</p>
          <div className="actions">
            <button type="button" className="primary" onClick={add}>
              {t('weight.add')}
            </button>
          </div>
        </div>
      )}

      {latest && (
        <>
          <div className="wtop">
            <div className="wreadout" aria-live="polite">
              {pickedDay && pickedValue != null ? (
                <>
                  <b className="num">{kg(pickedValue)} kg</b>
                  <span className="note">{longDay(pickedDay)}</span>
                </>
              ) : (
                <>
                  <b className="num">{kg(latest.weight_kg)} kg</b>
                  <span className="note">{t('weight.latestOn', { day: longDay(latest.day) })}</span>
                </>
              )}
            </div>
            {moved != null && <p className="note wmoved">{t('weight.change', { value: signed(moved) })}</p>}
          </div>
          <div className="seg wranges" role="group" aria-label={t('stats.period')}>
            {WEIGHT_RANGES.map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={r === range}
                onClick={() => {
                  setRange(r);
                  setPicked(null);
                }}
              >
                {t(`weight.range.${r}`)}
              </button>
            ))}
          </div>
          {shown > 0 && (
            <section className="panel">
              <WeightLine
                days={days}
                values={values}
                weighed={weighed}
                height={300}
                summary={`${t('weight.title')} ${kg(latest.weight_kg)} kg`}
                onPick={setPicked}
              />
            </section>
          )}

          <section className="panel">
            <CardTitle>{t('weight.history')}</CardTitle>
            <ul className="whistory">
              {history.map((w) => (
                <li key={w.day}>
                  <button
                    type="button"
                    className="wrow"
                    title={t('weight.edit')}
                    aria-label={`${t('weight.edit')}: ${longDay(w.day)}, ${kg(w.weight_kg)} kg`}
                    onClick={() => setEditing({ day: w.day, weight: kg(w.weight_kg), old: w.day })}
                  >
                    <span>{longDay(w.day)}</span>
                    {w.step != null && w.step !== 0 && <span className="note num">{signed(w.step)}</span>}
                    <b className="num">{kg(w.weight_kg)} kg</b>
                  </button>
                  <button
                    type="button"
                    className="iconbtn"
                    aria-label={`${t('common.delete')}: ${longDay(w.day)}`}
                    title={t('common.delete')}
                    onClick={() => setDeleting(w.day)}
                  >
                    <MaterialIcon name="delete" />
                  </button>
                </li>
              ))}
            </ul>
          </section>
          <p className="note">{t('weight.profileNote')}</p>
        </>
      )}

      {editing && (
        <WeighingDialog
          editing={editing}
          today={today}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            setEditing(null);
            await changed();
          }}
        />
      )}
      {deleting && (
        <Modal title={t('common.delete')} onClose={() => setDeleting(null)}>
          <p>{t('weight.deleteAsk', { day: longDay(deleting) })}</p>
          {remove.isError && (
            <p className="note" role="alert">
              {t('common.error')}
            </p>
          )}
          <div className="actions end">
            <button type="button" onClick={() => setDeleting(null)}>
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="danger"
              disabled={remove.isPending}
              onClick={() => remove.mutate(deleting)}
            >
              <MaterialIcon name="delete" />
              {t('common.delete')}
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}

/** A weighing to add or change: its weight and its day (one a day: a second replaces it). */
function WeighingDialog({
  editing,
  today,
  onClose,
  onSaved,
}: {
  editing: Editing;
  today: string;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [weight, setWeight] = useState(editing.weight);
  const [day, setDay] = useState(editing.day);
  const value = parseWeight(weight);
  const valid = value != null && /^\d{4}-\d{2}-\d{2}$/.test(day) && day <= today;
  const save = useMutation({
    mutationFn: async (w: Weighing) => {
      const { error } = await api.PUT('/api/weight', { body: w });
      if (error) throw new Error('save');
      // A weighing moved to another day leaves the old one.
      if (editing.old && editing.old !== w.day)
        await api.DELETE('/api/weight/{day}', { params: { path: { day: editing.old } } });
    },
    onSuccess: onSaved,
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (valid) save.mutate({ day, weight_kg: value });
  };
  return (
    <Modal title={editing.old ? t('weight.edit') : t('weight.add')} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <label className="field">
          <b>{t('weight.kg')}</b>
          <input
            inputMode="decimal"
            autoComplete="off"
            // The dialog opens to type the weight.
            autoFocus
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
          />
        </label>
        <label className="field">
          <b>{t('weight.date')}</b>
          <input type="date" max={today} required value={day} onChange={(e) => setDay(e.target.value)} />
        </label>
        {!valid && weight.trim() !== '' && <p className="note">{t('weight.invalid')}</p>}
        {save.isError && (
          <p className="note" role="alert">
            {t('common.error')}
          </p>
        )}
        <div className="actions end">
          <button type="button" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="primary" disabled={!valid || save.isPending}>
            {t('weight.save')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
