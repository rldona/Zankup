/**
 * Skills, from Progress (as Strava's): the radar of the best power over twelve durations, the last
 * eight weeks over all time (or all time alone), and each skill (sprint, attack, climb) with its level
 * on a nine-step bar; a skill opens to its durations, each with its watts and its own bar.
 */
import {
  durationLabel,
  LEVELS,
  levelAt,
  levelName,
  levelWatts,
  SEGMENT_WIDTHS,
  segments,
  SKILLS,
  skillLevel,
  type Skill,
} from '@zankup/core';
import { Stack } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { Icon } from '../src/Icon';
import { locale, t } from '../src/i18n';
import { radarColours, skillColour, SkillsRadar, useSkills } from '../src/progress/Skills';
import { Panel } from '../src/progress/Panel';
import { ChartSkeleton } from '../src/Skeleton';
import { useTheme } from '../src/theme';
import { accent, InfoButton, Muted, Screen } from '../src/ui';

type Span = 'recent' | 'all';

export default function SkillsScreen() {
  const c = useTheme();
  const skills = useSkills();
  const [span, setSpan] = useState<Span>('recent');
  const [open, setOpen] = useState<Skill | null>(null);
  const [width, setWidth] = useState(0);
  // The duration whose levels are open (tapping its watts on the radar), as Strava's.
  const [picked, setPicked] = useState<number | null>(null);
  const colours = radarColours();
  const bests = span === 'recent' ? skills.recent : skills.all;
  const shapes = [
    { bests: skills.all, colour: colours.all, weight: skills.weight },
    ...(span === 'recent'
      ? [{ bests: skills.recent, colour: colours.recent, weight: skills.weight, dots: true }]
      : []),
  ];

  return (
    <Screen header>
      <Stack.Screen options={{ headerRight: () => <InfoButton text={t('progress.skillsInfo')} /> }} />
      <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} style={{ alignItems: 'center' }}>
        {skills.isPending || !width ? (
          <ChartSkeleton height={width || 320} />
        ) : (
          <SkillsRadar size={width} shapes={shapes} onPick={setPicked} />
        )}
      </View>
      {!skills.hasWeight && <Muted>{t('progress.skillsNoWeight', { kg: skills.weight })}</Muted>}

      <View style={{ flexDirection: 'row', gap: 20 }}>
        {(span === 'recent' ? (['recent', 'all'] as const) : (['all'] as const)).map((s) => (
          <View key={s} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colours[s] }} />
            <Muted style={{ fontSize: 13 }}>{t(`progress.skillSpan.${s}`)}</Muted>
          </View>
        ))}
      </View>
      <View
        style={{
          flexDirection: 'row',
          borderRadius: 999,
          borderWidth: 1,
          borderColor: c.line,
          overflow: 'hidden',
        }}
      >
        {(['recent', 'all'] as const).map((s) => {
          const on = s === span;
          return (
            <Pressable
              key={s}
              accessibilityRole="radio"
              accessibilityState={{ checked: on }}
              onPress={() => setSpan(s)}
              style={{
                flex: 1,
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center',
                gap: 6,
                paddingVertical: 10,
                backgroundColor: on ? c.surface2 : 'transparent',
              }}
            >
              {on && <Icon name="check" color={c.fg} size={18} />}
              <Text style={{ color: c.fg, fontSize: 14, fontWeight: on ? '700' : '400' }}>
                {t(`progress.skillSpan.${s}`)}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {SKILLS.map(({ skill, durations }) => {
        const levels = durations.map((d) => levelAt(bests.get(d) ?? 0, d, skills.weight));
        const level = skillLevel(levels);
        const unfolded = open === skill;
        const colour = skillColour(c, skill);
        return (
          <View key={skill} style={{ borderTopWidth: 1, borderColor: c.line, paddingTop: 14, gap: 12 }}>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ expanded: unfolded }}
              onPress={() => setOpen(unfolded ? null : skill)}
              style={{ gap: 8 }}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: c.fg, fontSize: 16 }}>{t(`progress.skill.${skill}`)}</Text>
                  <Muted style={{ fontSize: 13 }}>
                    {level > 0
                      ? `${t(`progress.level.${levelName(level)}` as never)} · ${t('progress.levelN', { level: levelName(level) })}`
                      : t('progress.noLevel')}
                  </Muted>
                </View>
                <Icon name={unfolded ? 'keyboard_arrow_up' : 'keyboard_arrow_down'} color={c.fg} size={26} />
              </View>
              <LevelBar level={level} colour={colour} height={8} />
            </Pressable>
            {unfolded &&
              durations.map((d, i) => (
                <View key={d} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                  <Text style={{ width: 54, color: c.fg, fontSize: 14 }}>{durationLabel(d)}</Text>
                  <Text style={{ width: 60, color: c.fg, fontSize: 14, fontVariant: ['tabular-nums'] }}>
                    {bests.get(d) ? `${Math.round(bests.get(d) ?? 0)} W` : '–'}
                  </Text>
                  <View style={{ flex: 1 }}>
                    <LevelBar level={levels[i]} colour={colour} height={6} />
                  </View>
                </View>
              ))}
            <View style={{ height: 4 }} />
          </View>
        );
      })}
      <Muted style={{ fontSize: 12 }}>{t('progress.skillsScale', { levels: LEVELS })}</Muted>
      <Panel open={picked != null} onClose={() => setPicked(null)}>
        {picked != null && (
          <DurationLevels
            duration={picked}
            span={span}
            recent={skills.recent}
            all={skills.all}
            weight={skills.weight}
          />
        )}
      </Panel>
    </Screen>
  );
}

/** Nine segments, narrower towards the top, filled up to the level. */
function LevelBar({ level, colour, height }: { level: number; colour: string; height: number }) {
  const c = useTheme();
  return (
    <View style={{ flexDirection: 'row', gap: 3 }}>
      {segments(level).map((fill, i) => (
        <View
          key={i}
          style={{
            flex: SEGMENT_WIDTHS[i],
            height,
            borderRadius: height / 2,
            backgroundColor: c.surface2,
            overflow: 'hidden',
          }}
        >
          <View style={{ width: `${fill * 100}%`, height, backgroundColor: colour }} />
        </View>
      ))}
    </View>
  );
}

/**
 * One duration's levels, as Strava's sheet: the nine levels from the best down, the watts each needs at
 * the rider's weight, and for the rider's best (of the last eight weeks or of all time) its level
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
  const c = useTheme();
  const [span, setSpan] = useState<Span>(first);
  const best = (span === 'recent' ? recent : all).get(duration) ?? 0;
  const mine = best > 0 ? levelName(levelAt(best, duration, weight)) : 0;
  const watts = (w: number) => `${Math.round(w).toLocaleString(locale)} W`;
  const header = { color: c.fg, fontSize: 12, letterSpacing: 1.5 } as const;
  return (
    <View style={{ gap: 14, paddingBottom: 8 }}>
      <View style={{ paddingHorizontal: 20, gap: 6 }}>
        <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>
          {t('progress.skillPower', { duration: durationLabel(duration) })}
        </Text>
        <Text style={{ color: c.fg, fontSize: 15, lineHeight: 22 }}>
          {t('progress.skillSheetIntro', { kg: Math.round(weight) })}
        </Text>
      </View>
      <View
        style={{
          flexDirection: 'row',
          marginHorizontal: 20,
          borderRadius: 999,
          borderWidth: 1,
          borderColor: c.line,
          overflow: 'hidden',
        }}
      >
        {(['recent', 'all'] as const).map((s) => {
          const on = s === span;
          return (
            <Pressable
              key={s}
              accessibilityRole="radio"
              accessibilityState={{ checked: on }}
              onPress={() => setSpan(s)}
              style={{
                flex: 1,
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center',
                gap: 6,
                paddingVertical: 10,
                backgroundColor: on ? c.surface2 : 'transparent',
              }}
            >
              {on && <Icon name="check" color={c.fg} size={18} />}
              <Text style={{ color: c.fg, fontSize: 14, fontWeight: on ? '700' : '400' }}>
                {t(`progress.skillSpan.${s}`)}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <View>
        <View
          style={{
            flexDirection: 'row',
            paddingHorizontal: 20,
            paddingVertical: 12,
            backgroundColor: c.surface2,
          }}
        >
          <Text style={[header, { flex: 1 }]}>{t('progress.levels').toUpperCase()}</Text>
          <Text style={[header, { width: 90 }]}>{t('progress.power').toUpperCase()}</Text>
          <Text style={[header, { width: 90 }]}>{t('progress.yourBest').toUpperCase()}</Text>
        </View>
        {Array.from({ length: LEVELS }, (_, i) => LEVELS - i).map((level) => {
          const need = levelWatts(level, duration, weight);
          const here = level === mine;
          const text = { color: c.fg, fontSize: 16, fontWeight: here ? '800' : '400' } as const;
          return (
            <View
              key={level}
              accessibilityLabel={`${t(`progress.level.${level}` as never)}: ${watts(need)}`}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingHorizontal: 20,
                paddingVertical: 13,
                backgroundColor: here ? c.surface2 : 'transparent',
                borderLeftWidth: 4,
                borderLeftColor: here ? accent(c) : 'transparent',
              }}
            >
              <Text style={[text, { flex: 1 }]}>{t(`progress.level.${level}` as never)}</Text>
              <Text style={[text, { width: 90, fontVariant: ['tabular-nums'] }]}>
                {level === 1 ? '–' : watts(need)}
              </Text>
              <Text style={[text, { width: 90, fontVariant: ['tabular-nums'] }]}>
                {here ? watts(best) : best > 0 && level > mine ? `+${watts(need - best)}` : ''}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}
