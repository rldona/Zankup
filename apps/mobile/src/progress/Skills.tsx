/**
 * Skills on Progress (as Strava's): the radar of a rider's best power over twelve durations, each on
 * the nine-level scale (skills.ts), ringed by the three skills in their colours, for the last eight
 * weeks over all time. The card shows the strongest skill beside both radars; app/skills.tsx the
 * radar in full with each skill's level.
 */
import { curvesQuery, meQuery } from '@zankup/core';
import {
  Canvas,
  Circle,
  Group,
  Line,
  matchFont,
  Path,
  Skia,
  TextPath,
  vec,
} from '@shopify/react-native-skia';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../auth';
import { useCanvasReady } from '../charts';
import { Icon } from '../Icon';
import { t } from '../i18n';
import { CardSkeleton } from '../Skeleton';
import { useTheme, type Palette } from '../theme';
import { accent, Card, Muted, Tappable } from '../ui';
import { isoDay } from './fitness';
import {
  DURATIONS,
  durationLabel,
  LEVELS,
  levelAt,
  radialShare,
  SKILLS,
  strongest,
  type Skill,
} from './skillLevels';
import { addDays } from './weeks';

/** Without a weight in the profile, the scale is read for a rider of this much. */
export const REFERENCE_KG = 75;

/** The skills' colours, as Strava's (sampled from its app), the same on every theme. */
const SKILL_COLOURS: Record<Skill, string> = { sprint: '#0160cf', attack: '#59a61c', climb: '#fb7432' };

export const skillColour = (_c: Palette, skill: Skill) => SKILL_COLOURS[skill];

/** The two radars' colours, as Strava's: all time in purple, the last eight weeks in pink over it. */
export const radarColours = () => ({ all: '#823bae', recent: '#d97ed1' });

/** The best power at each duration, for the last eight weeks and for all time, and the weight. */
export function useSkills() {
  const api = useApi();
  const today = isoDay(new Date());
  const recent = useQuery(curvesQuery(api, 'power', { from: addDays(today, -55), to: addDays(today, 1) }));
  const all = useQuery(curvesQuery(api, 'power', { from: '2000-01-01', to: addDays(today, 1) }));
  const me = useQuery(meQuery(api));
  const map = (points?: { duration_s: number; value: number }[]) =>
    new Map((points ?? []).map((p) => [p.duration_s, p.value]));
  const weight = me.data?.weight_kg ?? null;
  return {
    recent: map(recent.data),
    all: map(all.data),
    weight: weight ?? REFERENCE_KG,
    hasWeight: weight != null,
    isPending: recent.isPending || all.isPending,
  };
}

/** The duration on the ring: on top, read along it; underneath, turned so it still reads upright. */
const ringFont = (size: number) => matchFont({ fontFamily: 'sans-serif', fontSize: size, fontWeight: '700' });

/**
 * The radar, as Strava's: rings for the levels (spaced as the level bar's segments), a spoke per
 * duration, the skills round it as a coloured ring with each duration written on it, and one or two
 * shapes. With labels, each duration's watts outside the ring; the card's small one has none.
 */
export function SkillsRadar({
  size,
  shapes,
  labels = true,
  onPick,
}: {
  size: number;
  /** Each shape's best power by duration and its colour, the first drawn first (under). */
  shapes: { bests: Map<number, number>; colour: string; weight: number; dots?: boolean }[];
  labels?: boolean;
  /** A duration's watts were tapped (Skills opens its levels). */
  onPick?: (duration: number) => void;
}) {
  const c = useTheme();
  const ready = useCanvasReady(size);
  const centre = size / 2;
  const band = labels ? size * 0.042 : size * 0.06;
  // With labels the ring leaves room round it for the watts (as Strava's, about a third of the width).
  const outer = labels ? size * 0.33 : size / 2 - band / 2 - 1;
  const inner = outer - band / 2 - 3;
  const step = (Math.PI * 2) / DURATIONS.length;
  const angle = (i: number) => -Math.PI / 2 + i * step;
  const at = (i: number, r: number) => vec(centre + r * Math.cos(angle(i)), centre + r * Math.sin(angle(i)));
  const point = (bests: Map<number, number>, weight: number, i: number) =>
    at(i, inner * radialShare(levelAt(bests.get(DURATIONS[i]) ?? 0, DURATIONS[i], weight)));
  // Paths as SVG: this Skia builds them from strings.
  const shape = (bests: Map<number, number>, weight: number) =>
    DURATIONS.map((_, i) => {
      const p = point(bests, weight, i);
      return `${i ? 'L' : 'M'}${p.x} ${p.y}`;
    }).join('') + 'Z';
  const arcPoint = (a: number, r: number) => `${centre + r * Math.cos(a)} ${centre + r * Math.sin(a)}`;
  const arc = (from: number, to: number) => {
    const gap = 0.014;
    const a0 = angle(from) - step / 2 + gap;
    const a1 = angle(to) + step / 2 - gap;
    return `M${arcPoint(a0, outer)}A${outer} ${outer} 0 ${a1 - a0 > Math.PI ? 1 : 0} 1 ${arcPoint(a1, outer)}`;
  };
  const fontSize = band * 0.68;
  const font = labels ? ringFont(fontSize) : null;
  // Each duration's words along the ring, centred on its spoke: clockwise on the top half; on the
  // bottom half the other way round (and on the outer side of the middle line), so they read upright.
  const label = (i: number, text: string) => {
    if (!font) return null;
    const a = angle(i);
    const below = Math.sin(a) > 0.05;
    const r = below ? outer + fontSize * 0.35 : outer - fontSize * 0.35;
    const half = font.measureText(text).width / 2 / r;
    const [s, e] = below ? [a + half, a - half] : [a - half, a + half];
    return Skia.Path.MakeFromSVGString(`M${arcPoint(s, r)}A${r} ${r} 0 0 ${below ? 0 : 1} ${arcPoint(e, r)}`);
  };
  const skillOf = (i: number) => {
    let n = i;
    for (const s of SKILLS) {
      if (n < s.durations.length) return s.skill;
      n -= s.durations.length;
    }
    return 'climb' as Skill;
  };
  const top = shapes.at(-1);
  return (
    <View style={{ width: size, height: size }}>
      <Canvas style={{ width: size, height: size, opacity: ready ? 1 : 0 }}>
        {ready && (
          <Group>
            {Array.from({ length: LEVELS }, (_, k) => (
              <Circle
                key={k}
                cx={centre}
                cy={centre}
                r={inner * radialShare(k + 1)}
                style="stroke"
                strokeWidth={labels ? 1 : 0.6}
                color={c.muted}
                opacity={0.45}
              />
            ))}
            {DURATIONS.map((_, i) => (
              <Line
                key={i}
                p1={vec(centre, centre)}
                p2={at(i, inner)}
                color={c.muted}
                opacity={0.45}
                strokeWidth={labels ? 1 : 0.6}
              />
            ))}
            {SKILLS.map(({ skill, durations }, k) => {
              // Where the skill's first spoke is: after the durations of the skills before it.
              const from = SKILLS.slice(0, k).reduce((n, x) => n + x.durations.length, 0);
              return (
                <Path
                  key={skill}
                  path={arc(from, from + durations.length - 1)}
                  style="stroke"
                  strokeWidth={band}
                  color={skillColour(c, skill)}
                />
              );
            })}
            {font &&
              DURATIONS.map((d, i) => {
                const path = label(i, durationLabel(d));
                return path ? (
                  <TextPath
                    key={d}
                    path={path}
                    text={durationLabel(d)}
                    font={font}
                    color={skillOf(i) === 'sprint' ? '#ffffff' : '#141414'}
                  />
                ) : null;
              })}
            {shapes.map((s, i) => (
              <Path key={i} path={shape(s.bests, s.weight)} color={s.colour} opacity={0.9} />
            ))}
            {top?.dots &&
              DURATIONS.map((_, i) => {
                const p = point(top.bests, top.weight, i);
                return <Circle key={i} cx={p.x} cy={p.y} r={labels ? 2.5 : 1.2} color="#ffffff" />;
              })}
          </Group>
        )}
      </Canvas>
      {labels &&
        DURATIONS.map((d, i) => {
          const p = at(i, outer + band / 2 + 26);
          const w = top?.bests.get(d);
          return (
            <Pressable
              key={d}
              accessibilityRole={onPick ? 'button' : undefined}
              disabled={!onPick}
              hitSlop={10}
              onPress={() => onPick?.(d)}
              style={{ position: 'absolute', left: p.x - 36, top: p.y - 13, width: 72, alignItems: 'center' }}
            >
              {/* Underlined as Strava's: a thick line a little below the figure, as wide as it. */}
              <View style={{ borderBottomWidth: w ? 2 : 0, borderColor: c.fg, paddingBottom: 3 }}>
                <Text
                  accessibilityLabel={`${durationLabel(d)}: ${w ? `${Math.round(w)} W` : '–'}`}
                  style={{ color: c.fg, fontSize: 15, fontWeight: '800', fontVariant: ['tabular-nums'] }}
                >
                  {w ? `${Math.round(w)} W` : '–'}
                </Text>
              </View>
            </Pressable>
          );
        })}
    </View>
  );
}

export function SkillsCard() {
  const c = useTheme();
  const skills = useSkills();
  if (skills.isPending) return <CardSkeleton lines={3} />;
  const best = strongest(skills.recent, skills.weight);
  const colours = radarColours();
  return (
    <Tappable
      accessibilityRole="button"
      accessibilityLabel={`${t('progress.skills')}${best ? `: ${t(`progress.skill.${best.skill}`)} ${durationLabel(best.duration)} ${Math.round(best.watts)} W` : ''}`}
      onPress={() => router.push('/skills')}
    >
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Icon name="bolt" color={accent(c)} size={26} />
          <Text numberOfLines={1} style={{ flex: 1, color: c.fg, fontSize: 16, fontWeight: '700' }}>
            {t('progress.skills')}
          </Text>
          <Icon name="chevron_right" color={c.muted} size={22} />
        </View>
        {!best ? (
          <Muted style={{ marginTop: 12 }}>{t('progress.noSkills')}</Muted>
        ) : (
          <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 8, gap: 12 }}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={{ color: c.fg, fontSize: 14 }}>
                {t(`progress.skill.${best.skill}`)} · {durationLabel(best.duration)}
              </Text>
              <Text style={{ color: c.fg, fontSize: 30, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                {Math.round(best.watts)} W
              </Text>
              {/* The two shapes the radar shows, as on the Skills page. */}
              <View style={{ gap: 4, marginTop: 6 }}>
                {(['recent', 'all'] as const).map((span) => (
                  <View key={span} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colours[span] }} />
                    <Muted style={{ fontSize: 12 }}>{t(`progress.skillSpan.${span}`)}</Muted>
                  </View>
                ))}
              </View>
            </View>
            <SkillsRadar
              size={150}
              labels={false}
              shapes={[
                { bests: skills.all, colour: colours.all, weight: skills.weight },
                { bests: skills.recent, colour: colours.recent, weight: skills.weight },
              ]}
            />
          </View>
        )}
      </Card>
    </Tappable>
  );
}
