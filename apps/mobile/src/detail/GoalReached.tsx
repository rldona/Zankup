/**
 * The goals an activity completed (the one that took its period's total over the target): a line on the
 * feed's card and a banner on the activity's page, each opening the goals.
 */
import { type ReachedGoal as Reached, type GoalMetric } from '@zankup/core';
import { router } from 'expo-router';
import { Text, View } from 'react-native';

import { Icon } from '../Icon';
import { t } from '../i18n';
import { goalWords } from '../progress/GoalsCard';
import { useVolumeFormat } from '../progress/VolumeCard';
import { useTheme } from '../theme';
import { accent, Card, Muted, Tappable } from '../ui';

/** "Anual: Objetivo de distancia · 1.000 km · Carrera". */
function useGoalLine() {
  const format = useVolumeFormat();
  return (g: Reached) => {
    const words = goalWords(g as never);
    const target = g.metric === 'count' ? '' : ` · ${format.value(g.metric as GoalMetric, g.target, 0)}`;
    return `${words.title}${target} · ${words.note}`;
  };
}

/** On the feed's card: a capsule with a trophy and the goal, so it stands out. */
export function GoalReachedLine({ goals }: { goals: Reached[] | undefined }) {
  const c = useTheme();
  const line = useGoalLine();
  if (!goals?.length) return null;
  return (
    <View
      style={{
        alignSelf: 'flex-start',
        maxWidth: '100%',
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        paddingVertical: 6,
        paddingHorizontal: 12,
        borderRadius: 999,
        backgroundColor: c.surface2,
      }}
    >
      <Icon name="trophy" color={accent(c)} size={16} />
      <Text numberOfLines={1} style={{ flexShrink: 1, color: c.fg, fontSize: 13, fontWeight: '600' }}>
        <Text style={{ fontWeight: '800' }}>{t('goal.reached')}</Text> · {goals.map(line).join(' · ')}
      </Text>
    </View>
  );
}

/** On the activity's page: the congratulation, each goal under it. */
export function GoalReachedBanner({ goals }: { goals: Reached[] | undefined }) {
  const c = useTheme();
  const line = useGoalLine();
  if (!goals?.length) return null;
  return (
    <Tappable accessibilityRole="button" onPress={() => router.push('/objectives')}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <Icon name="trophy" color={accent(c)} size={32} />
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ color: c.fg, fontSize: 16, fontWeight: '800' }}>
              {t('goal.reachedTitle', { count: goals.length })}
            </Text>
            {goals.map((g) => (
              <Muted key={g.goal_id} style={{ fontSize: 13 }}>
                {line(g)}
              </Muted>
            ))}
          </View>
          <Icon name="chevron_right" color={c.muted} size={22} />
        </View>
      </Card>
    </Tappable>
  );
}
