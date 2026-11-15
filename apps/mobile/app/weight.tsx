/**
 * Weight, from Health: how it changed over a month to all time, as a line through the weighings, and
 * every weighing below, newest first. "+" adds one (a day can have one: a second replaces it), a
 * weighing opens to be changed, and a long press deletes it. The latest is the profile's weight.
 */
import { weightQuery } from '@zankup/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { useApi } from '../src/auth';
import { Chart } from '../src/charts';
import { Icon } from '../src/Icon';
import { locale, t } from '../src/i18n';
import { isoDay } from '../src/progress/fitness';
import { Panel } from '../src/progress/Panel';
import { fromDayMonthYear, toDayMonthYear, typeDayMonthYear } from '../src/profile/helpers';
import { useTheme } from '../src/theme';
import { accent, Button, Card, Field, Heading, Muted, onAccent, Screen } from '../src/ui';
import {
  change,
  dailyWeights,
  parseWeight,
  rangeStart,
  WEIGHT_RANGES,
  type Weighing,
  type WeightRange,
} from '../src/weight';

const kg = (n: number) => n.toLocaleString(locale, { maximumFractionDigits: 1 });
const signed = (n: number) => `${n > 0 ? '+' : n < 0 ? '−' : ''}${kg(Math.abs(n))}`;
const shortDay = (day: string) =>
  new Date(`${day}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'short' });
const longDay = (day: string) =>
  new Date(`${day}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });

export default function WeightScreen() {
  const api = useApi();
  const c = useTheme();
  const client = useQueryClient();
  const today = isoDay(new Date());
  const weight = useQuery(weightQuery(api));
  const weighings = weight.data ?? [];
  const [range, setRange] = useState<WeightRange>('3m');
  // The weighing being written: a new one, or one opened from the list.
  const [editing, setEditing] = useState<{ day: string; weight: string; old?: string } | null>(null);

  const changed = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['weight'] }),
      client.invalidateQueries({ queryKey: ['me'] }),
    ]);
  const save = useMutation({
    mutationFn: async (w: { day: string; weight_kg: number; old?: string }) => {
      const { error } = await api.PUT('/api/weight', { body: { day: w.day, weight_kg: w.weight_kg } });
      if (error) throw new Error('save');
      // A weighing moved to another day leaves the old one.
      if (w.old && w.old !== w.day)
        await api.DELETE('/api/weight/{day}', { params: { path: { day: w.old } } });
    },
    onSuccess: async () => {
      setEditing(null);
      await changed();
    },
  });
  const remove = useMutation({
    mutationFn: async (day: string) => {
      await api.DELETE('/api/weight/{day}', { params: { path: { day } } });
    },
    onSuccess: changed,
  });

  const from = rangeStart(range, today, weighings);
  const values = dailyWeights(weighings, from, today);
  const days: string[] = [];
  for (let d = new Date(`${from}T12:00:00`); isoDay(d) <= today; d.setDate(d.getDate() + 1))
    days.push(shortDay(isoDay(d)));
  const shown = values.filter((v): v is number => v != null);
  const latest = weighings.at(-1);
  const moved = change(weighings, from);

  const day = editing ? fromDayMonthYear(editing.day) : null;
  const value = editing ? parseWeight(editing.weight) : null;
  const valid = day != null && value != null && day <= today;

  return (
    <Screen header>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={t('weight.add')}
              hitSlop={10}
              onPress={() =>
                setEditing({ day: toDayMonthYear(today), weight: latest ? kg(latest.weight_kg) : '' })
              }
            >
              <Icon name="add" color={c.fg} size={26} />
            </Pressable>
          ),
        }}
      />

      {latest ? (
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 12 }}>
          <View style={{ flex: 1 }}>
            <Text style={{ color: c.fg, fontSize: 32, fontWeight: '800' }}>{kg(latest.weight_kg)} kg</Text>
            <Muted style={{ fontSize: 13 }}>{t('weight.latestOn', { day: longDay(latest.day) })}</Muted>
          </View>
          {moved != null && (
            <Muted style={{ fontSize: 14 }}>{t('weight.change', { value: signed(moved) })}</Muted>
          )}
        </View>
      ) : (
        weight.isSuccess && (
          <Card>
            <Text style={{ color: c.fg, fontSize: 15, lineHeight: 22 }}>{t('weight.empty')}</Text>
            <Button primary onPress={() => setEditing({ day: toDayMonthYear(today), weight: '' })}>
              {t('weight.add')}
            </Button>
          </Card>
        )
      )}
      {weight.isError && <Muted accessibilityRole="alert">{t('common.error')}</Muted>}

      {shown.length > 0 && (
        <Card>
          <Chart
            height={200}
            days={days}
            label={`${t('weight.title')} ${kg(shown.at(-1) ?? 0)} kg`}
            layers={[
              {
                kind: 'line',
                label: t('weight.kg'),
                color: String(accent(c)),
                values,
                width: 3,
                hideLegend: true,
                domain: [Math.min(...shown) - 1, Math.max(...shown) + 1],
              },
            ]}
          />
        </Card>
      )}

      {weighings.length > 0 && (
        <View style={{ flexDirection: 'row', gap: 4 }}>
          {WEIGHT_RANGES.map((r) => {
            const on = r === range;
            return (
              <Pressable
                key={r}
                accessibilityRole="radio"
                accessibilityState={{ checked: on }}
                onPress={() => setRange(r)}
                style={{
                  flex: 1,
                  alignItems: 'center',
                  paddingVertical: 9,
                  borderRadius: 999,
                  backgroundColor: on ? accent(c) : 'transparent',
                }}
              >
                <Text style={{ color: on ? onAccent(c) : accent(c), fontSize: 14, fontWeight: '800' }}>
                  {t(`weight.range.${r}`)}
                </Text>
              </Pressable>
            );
          })}
        </View>
      )}

      {weighings.length > 0 && (
        <>
          <Heading>{t('weight.history')}</Heading>
          <Card style={{ paddingVertical: 4, gap: 0 }}>
            {[...weighings].reverse().map((w: Weighing, i, all) => {
              const before = all[i + 1];
              const step = before ? Math.round((w.weight_kg - before.weight_kg) * 10) / 10 : null;
              return (
                <Pressable
                  key={w.day}
                  accessibilityRole="button"
                  accessibilityHint={t('weight.edit')}
                  onPress={() =>
                    setEditing({ day: toDayMonthYear(w.day), weight: kg(w.weight_kg), old: w.day })
                  }
                  onLongPress={() =>
                    Alert.alert(t('weight.deleteAsk', { day: longDay(w.day) }), undefined, [
                      { text: t('common.cancel'), style: 'cancel' },
                      { text: t('common.delete'), style: 'destructive', onPress: () => remove.mutate(w.day) },
                    ])
                  }
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    paddingVertical: 12,
                    borderBottomWidth: i === all.length - 1 ? 0 : 1,
                    borderBottomColor: c.line,
                  }}
                >
                  <Text style={{ flex: 1, color: c.fg, fontSize: 15 }}>{longDay(w.day)}</Text>
                  {step != null && step !== 0 && (
                    <Muted style={{ fontSize: 13, marginRight: 12 }}>{signed(step)}</Muted>
                  )}
                  <Text style={{ color: c.fg, fontSize: 15, fontWeight: '700' }}>{kg(w.weight_kg)} kg</Text>
                </Pressable>
              );
            })}
          </Card>
          <Muted style={{ fontSize: 13 }}>{t('weight.profileNote')}</Muted>
        </>
      )}

      <Panel open={editing != null} onClose={() => setEditing(null)}>
        {editing && (
          <View style={{ gap: 14, paddingHorizontal: 20, paddingBottom: 8 }}>
            <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>
              {editing.old ? t('weight.edit') : t('weight.add')}
            </Text>
            <Field
              label={t('weight.kg')}
              keyboardType="decimal-pad"
              autoFocus
              value={editing.weight}
              onChangeText={(text) => setEditing({ ...editing, weight: text })}
            />
            <Field
              label={t('weight.day')}
              keyboardType="number-pad"
              value={editing.day}
              onChangeText={(text) => setEditing({ ...editing, day: typeDayMonthYear(text) })}
            />
            {!valid && editing.weight !== '' && <Muted>{t('weight.invalid')}</Muted>}
            {save.isError && <Muted accessibilityRole="alert">{t('common.error')}</Muted>}
            <Button
              primary
              disabled={!valid || save.isPending}
              onPress={() => day && value != null && save.mutate({ day, weight_kg: value, old: editing.old })}
            >
              {t('weight.save')}
            </Button>
          </View>
        )}
      </Panel>
    </Screen>
  );
}
