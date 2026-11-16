/**
 * Skills on Progress (OS-1004), as the app's and Strava's: the radar of a rider's best power over twelve
 * durations, each on the nine-level scale, ringed by sprint, attack and climb in their colours, the last
 * eight weeks over all time. The card shows the strongest skill beside the small radar; the page the
 * radar in full with each duration's watts (a click opens its nine levels for the rider's weight in a
 * dialog) and each skill's level, unfolding to its durations.
 */
import {
  DURATIONS,
  durationLabel,
  LEVELS,
  levelAt,
  levelName,
  levelWatts,
  RADAR_COLOURS,
  radialShare,
  SEGMENT_WIDTHS,
  segments,
  SKILL_COLOURS,
  SKILL_TEXT,
  SKILLS,
  skillLevel,
  skillOf,
  strongest,
  type MessageKey,
  type Skill,
} from '@zankup/core';
import { useId, useState } from 'react';
import { Link } from 'react-router';

import { Modal } from '../../detail/Tools';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { Bone, CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle, InfoTip } from '../../ui';
import { useSkills } from './data';

type Span = 'recent' | 'all';
type Shape = { bests: Map<number, number>; colour: string; weight: number; dots?: boolean };

const levelLabel = (level: number) => t(`progress.level.${level}` as MessageKey);
const watts = (w: number) => `${Math.round(w).toLocaleString(locale)} W`;

/**
 * The radar, drawn in SVG as the app's: rings for the levels (spaced as the level bar's segments), a
 * spoke per duration, the skills round it as a coloured ring with each duration written along it, and
 * one or two shapes. With labels, each duration's watts outside the ring, underlined, as buttons.
 */
function SkillsRadar({
  shapes,
  labels = true,
  onPick,
}: {
  /** Each shape's best power by duration and its colour, the first drawn first (under). */
  shapes: Shape[];
  labels?: boolean;
  onPick?: (duration: number) => void;
}) {
  const id = useId();
  const [hover, setHover] = useState<number | null>(null);
  const size = 400;
  const centre = size / 2;
  const band = labels ? size * 0.042 : size * 0.06;
  // With labels the ring leaves room round it for the watts (as Strava's, about a third of the width).
  const outer = labels ? size * 0.33 : size / 2 - band / 2 - 1;
  const inner = outer - band / 2 - 3;
  const step = (Math.PI * 2) / DURATIONS.length;
  const angle = (i: number) => -Math.PI / 2 + i * step;
  const at = (i: number, r: number) => ({
    x: centre + r * Math.cos(angle(i)),
    y: centre + r * Math.sin(angle(i)),
  });
  const point = (s: Shape, i: number) => {
    const d = DURATIONS[i] ?? 0;
    return at(i, inner * radialShare(levelAt(s.bests.get(d) ?? 0, d, s.weight)));
  };
  const polygon = (s: Shape) =>
    DURATIONS.map((_, i) => {
      const p = point(s, i);
      return `${p.x.toFixed(1)},${p.y.toFixed(1)}`;
    }).join(' ');
  const arcPoint = (a: number, r: number) =>
    `${(centre + r * Math.cos(a)).toFixed(2)} ${(centre + r * Math.sin(a)).toFixed(2)}`;
  const arc = (from: number, to: number) => {
    const gap = 0.014;
    const a0 = angle(from) - step / 2 + gap;
    const a1 = angle(to) + step / 2 - gap;
    return `M${arcPoint(a0, outer)}A${outer} ${outer} 0 ${a1 - a0 > Math.PI ? 1 : 0} 1 ${arcPoint(a1, outer)}`;
  };
  const fontSize = band * 0.68;
  // Each duration's words along the ring, centred on its spoke: clockwise on the top half; on the
  // bottom half the other way round, so they read upright.
  const labelPath = (i: number) => {
    const a = angle(i);
    const below = Math.sin(a) > 0.05;
    const r = below ? outer + fontSize * 0.35 : outer - fontSize * 0.35;
    const [s, e] = below ? [a + step / 2, a - step / 2] : [a - step / 2, a + step / 2];
    return `M${arcPoint(s, r)}A${r} ${r} 0 0 ${below ? 0 : 1} ${arcPoint(e, r)}`;
  };
  const top = shapes.at(-1);
  return (
    <div className={`pk-radar${labels ? ' big' : ''}`}>
      <svg viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        {Array.from({ length: LEVELS }, (_, k) => (
          <circle key={k} className="pk-grid" cx={centre} cy={centre} r={inner * radialShare(k + 1)} />
        ))}
        {DURATIONS.map((_, i) => {
          const p = at(i, inner);
          return (
            <line
              key={i}
              className={`pk-grid${hover === i ? ' on' : ''}`}
              x1={centre}
              y1={centre}
              x2={p.x}
              y2={p.y}
            />
          );
        })}
        {SKILLS.map(({ skill, durations }, k) => {
          // Where the skill's first spoke is: after the durations of the skills before it.
          const from = SKILLS.slice(0, k).reduce((n, x) => n + x.durations.length, 0);
          return (
            <path
              key={skill}
              d={arc(from, from + durations.length - 1)}
              fill="none"
              strokeWidth={band}
              stroke={SKILL_COLOURS[skill]}
            />
          );
        })}
        {labels &&
          DURATIONS.map((d, i) => (
            <g key={d}>
              <path id={`${id}-l${i}`} d={labelPath(i)} fill="none" />
              <text fontSize={fontSize} className="pk-ringtext" fill={SKILL_TEXT[skillOf(i)]}>
                <textPath href={`#${id}-l${i}`} startOffset="50%" textAnchor="middle">
                  {durationLabel(d)}
                </textPath>
              </text>
            </g>
          ))}
        {shapes.map((s) => (
          <polygon key={s.colour} className="pk-shape" points={polygon(s)} fill={s.colour} />
        ))}
        {top?.dots &&
          DURATIONS.map((_, i) => {
            const p = point(top, i);
            return <circle key={i} className="pk-dot" cx={p.x} cy={p.y} r={labels ? 3 : 1.5} />;
          })}
      </svg>
      {labels &&
        DURATIONS.map((d, i) => {
          const p = at(i, outer + band / 2 + size * 0.075);
          const w = top?.bests.get(d);
          const level = w && top ? levelName(levelAt(w, d, top.weight)) : null;
          return (
            <button
              key={d}
              type="button"
              className={`pk-watts num${w ? ' set' : ''}`}
              style={{ left: `${(p.x / size) * 100}%`, top: `${(p.y / size) * 100}%` }}
              aria-label={`${durationLabel(d)}: ${w ? watts(w) : '–'}`}
              title={`${durationLabel(d)}${w ? ` · ${watts(w)}` : ''}${level ? ` · ${levelLabel(level)}` : ''}`}
              disabled={!onPick}
              onClick={() => onPick?.(d)}
              onPointerEnter={() => setHover(i)}
              onPointerLeave={() => setHover(null)}
              onFocus={() => setHover(i)}
              onBlur={() => setHover(null)}
            >
              {w ? watts(w) : '–'}
            </button>
          );
        })}
    </div>
  );
}

function Legend({ spans }: { spans: readonly Span[] }) {
  return (
    <ul className="pk-legend">
      {spans.map((s) => (
        <li key={s}>
          <i style={{ background: RADAR_COLOURS[s] }} />
          {t(`progress.skillSpan.${s}`)}
        </li>
      ))}
    </ul>
  );
}

export function SkillsCard() {
  const skills = useSkills();
  if (skills.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const best = strongest(skills.recent, skills.weight);
  return (
    <Link to="/progress/skills" className="panel pk-card">
      <CardTitle right={<MaterialIcon name="chevron_right" />}>
        <MaterialIcon name="bolt" />
        {t('progress.skills')}
      </CardTitle>
      {!best ? (
        <p className="note">{t('progress.noSkills')}</p>
      ) : (
        <div className="pk-cardbody">
          <div>
            <span>
              {t(`progress.skill.${best.skill}`)} · {durationLabel(best.duration)}
            </span>
            <b className="num pk-figure">{watts(best.watts)}</b>
            <Legend spans={['recent', 'all']} />
          </div>
          <SkillsRadar
            labels={false}
            shapes={[
              { bests: skills.all, colour: RADAR_COLOURS.all, weight: skills.weight },
              { bests: skills.recent, colour: RADAR_COLOURS.recent, weight: skills.weight },
            ]}
          />
        </div>
      )}
    </Link>
  );
}

function SpanSwitch({ span, onChange }: { span: Span; onChange: (span: Span) => void }) {
  return (
    <div className="seg pk-span" role="group" aria-label={t('progress.skills')}>
      {(['recent', 'all'] as const).map((s) => (
        <button key={s} type="button" aria-pressed={s === span} onClick={() => onChange(s)}>
          {s === span && <MaterialIcon name="check" />}
          {t(`progress.skillSpan.${s}`)}
        </button>
      ))}
    </div>
  );
}

/** Nine segments, narrower towards the top, filled up to the level. */
function LevelBar({ level, colour, thin = false }: { level: number; colour: string; thin?: boolean }) {
  return (
    <span className={`pk-bar${thin ? ' thin' : ''}`} aria-hidden="true">
      {segments(level).map((fill, i) => (
        <span key={i} style={{ flexGrow: SEGMENT_WIDTHS[i] }}>
          <i style={{ width: `${fill * 100}%`, background: colour }} />
        </span>
      ))}
    </span>
  );
}

export function SkillsPage() {
  const skills = useSkills();
  const [span, setSpan] = useState<Span>('recent');
  const [open, setOpen] = useState<Skill | null>(null);
  // The duration whose levels are open (clicking its watts on the radar), as Strava's.
  const [picked, setPicked] = useState<number | null>(null);
  const bests = span === 'recent' ? skills.recent : skills.all;
  const shapes: Shape[] = [
    { bests: skills.all, colour: RADAR_COLOURS.all, weight: skills.weight },
    ...(span === 'recent'
      ? [{ bests: skills.recent, colour: RADAR_COLOURS.recent, weight: skills.weight, dots: true }]
      : []),
  ];

  return (
    <section className="page pk-page">
      <div className="titlebar">
        <h1>{t('progress.skills')}</h1>
        <InfoTip text={t('progress.skillsInfo')} />
      </div>
      <div className="pk-layout">
        <div className="panel pk-chart">
          {skills.isPending ? (
            <Pulse>
              <Bone height={360} radius={180} />
            </Pulse>
          ) : (
            <SkillsRadar shapes={shapes} onPick={setPicked} />
          )}
          <Legend spans={span === 'recent' ? ['recent', 'all'] : ['all']} />
          {!skills.hasWeight && !skills.isPending && (
            <p className="note">{t('progress.skillsNoWeight', { kg: skills.weight })}</p>
          )}
        </div>
        <div className="panel pk-skills">
          <SpanSwitch span={span} onChange={setSpan} />
          {SKILLS.map(({ skill, durations }) => {
            const levels = durations.map((d) => levelAt(bests.get(d) ?? 0, d, skills.weight));
            const level = skillLevel(levels);
            const unfolded = open === skill;
            const colour = SKILL_COLOURS[skill];
            return (
              <div key={skill} className="pk-skill">
                <button
                  type="button"
                  aria-expanded={unfolded}
                  onClick={() => setOpen(unfolded ? null : skill)}
                >
                  <span className="pk-skillhead">
                    <span>
                      <b>{t(`progress.skill.${skill}`)}</b>
                      <small className="note">
                        {level > 0
                          ? `${levelLabel(levelName(level))} · ${t('progress.levelN', { level: levelName(level) })}`
                          : t('progress.noLevel')}
                      </small>
                    </span>
                    <MaterialIcon name={unfolded ? 'keyboard_arrow_up' : 'keyboard_arrow_down'} />
                  </span>
                  <LevelBar level={level} colour={colour} />
                </button>
                {unfolded && (
                  <ul className="pk-durations">
                    {durations.map((d, i) => (
                      <li key={d}>
                        <span>{durationLabel(d)}</span>
                        <span className="num">{bests.get(d) ? watts(bests.get(d) ?? 0) : '–'}</span>
                        <LevelBar level={levels[i] ?? 0} colour={colour} thin />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
          <p className="note">{t('progress.skillsScale', { levels: LEVELS })}</p>
        </div>
      </div>
      {picked != null && (
        <Modal
          title={t('progress.skillPower', { duration: durationLabel(picked) })}
          onClose={() => setPicked(null)}
          className="pk-modal"
        >
          <DurationLevels
            duration={picked}
            span={span}
            recent={skills.recent}
            all={skills.all}
            weight={skills.weight}
          />
        </Modal>
      )}
    </section>
  );
}

/**
 * One duration's levels, as Strava's sheet: the nine levels from the best down, the watts each needs at
 * the rider's weight and, for the rider's best (of the last eight weeks or of all time), its level
 * highlighted and how many watts each level above is away.
 */
function DurationLevels({
  duration,
  span: first,
  recent,
  all,
  weight,
}: {
  duration: number;
  span: Span;
  recent: Map<number, number>;
  all: Map<number, number>;
  weight: number;
}) {
  const [span, setSpan] = useState<Span>(first);
  const best = (span === 'recent' ? recent : all).get(duration) ?? 0;
  const mine = best > 0 ? levelName(levelAt(best, duration, weight)) : 0;
  return (
    <div className="pk-levels">
      <p>{t('progress.skillSheetIntro', { kg: Math.round(weight) })}</p>
      <SpanSwitch span={span} onChange={setSpan} />
      <table>
        <thead>
          <tr>
            <th>{t('progress.levels')}</th>
            <th>{t('progress.power')}</th>
            <th>{t('progress.yourBest')}</th>
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: LEVELS }, (_, i) => LEVELS - i).map((level) => {
            const need = levelWatts(level, duration, weight);
            const here = level === mine;
            return (
              <tr key={level} className={here ? 'here' : undefined} aria-current={here ? 'true' : undefined}>
                <td>{levelLabel(level)}</td>
                <td className="num">{level === 1 ? '–' : watts(need)}</td>
                <td className="num">
                  {here ? watts(best) : best > 0 && level > mine ? `+${watts(need - best)}` : ''}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
