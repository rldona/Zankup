import { render, waitFor } from '@testing-library/react';
import type { EChartsType } from 'echarts/core';
import { describe, expect, it, vi } from 'vitest';

import { EChart } from './EChart';
import { echarts } from './echarts';
import { morphOptions, withIds } from './morph';

/** Bars over a category axis, as the filtered charts draw them. */
const bars = (values: number[], names = ['Run']) => ({
  grid: { left: 40, right: 10, top: 10, bottom: 20 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: values.map((_, i) => `w${i}`) },
  yAxis: { type: 'value', max: Math.max(...values) },
  series: names.map((name) => ({ name, type: 'bar', stack: 'v', data: values })),
});

const draw = () => {
  const box = document.createElement('div');
  return echarts.init(box, undefined, { renderer: 'svg', width: 400, height: 200 });
};

/** The series models on the chart, which ECharts keeps when it morphs and builds anew when it redraws. */
const seriesOf = (chart: EChartsType) =>
  (chart as unknown as { getModel: () => { getSeries: () => object[] } }).getModel().getSeries();

describe('a new option morphs the chart on screen (OS-1012)', () => {
  it('gives each component an id from its name and shape, kept between filters', () => {
    const a = withIds(bars([1, 2, 3], ['Run', 'Ride']));
    const b = withIds(bars([4, 5, 6, 7], ['Ride', 'Run']));
    const ids = (o: ReturnType<typeof bars>) => o.series.map((s) => (s as { id?: string }).id);
    expect(ids(b)).toEqual([...ids(a)].reverse());
    // Another shape (a key more) is another component, drawn afresh rather than merged over.
    const c = withIds({ ...bars([1]), yAxis: { type: 'value', min: 0, max: 1 } });
    const yId = (o: { yAxis: unknown }) => (o.yAxis as { id: string }[])[0]?.id;
    expect(yId(c)).toBeDefined();
    expect(yId(c)).not.toBe(yId(a));
    // An id the chart gives itself stays.
    expect(withIds({ series: [{ id: 'value', type: 'line' }] }).series[0]).toEqual({
      id: 'value',
      type: 'line',
    });
  });

  it('merges by id, and starts afresh only for a chart just drawn or other global keys', () => {
    const a = withIds(bars([1, 2]));
    expect(morphOptions(null, a)).toEqual({ notMerge: true });
    expect(morphOptions(a, withIds(bars([3, 4])))).toEqual({
      replaceMerge: ['grid', 'tooltip', 'xAxis', 'yAxis', 'series'],
    });
    expect(morphOptions(a, withIds({ ...bars([3]), color: ['red'] }))).toEqual({ notMerge: true });
  });

  it('keeps the series ECharts drew, so its bars slide to the new values, and drops the ones gone', () => {
    const chart = draw();
    const a = withIds(bars([10, 20, 30], ['Run', 'Ride']));
    chart.setOption(a, morphOptions(null, a));
    const [run] = seriesOf(chart);
    const b = withIds(bars([40, 50], ['Run']));
    chart.setOption(b, morphOptions(a, b));
    expect(seriesOf(chart)).toEqual([run]);
    expect(seriesOf(chart)[0]).toBe(run);
    const yAxis = (chart.getOption() as { yAxis: { max: number }[] }).yAxis;
    expect(yAxis.map((y) => y.max)).toEqual([50]);
    // notMerge, as before, built every series anew: they grew from the floor.
    chart.setOption(b, { notMerge: true });
    expect(seriesOf(chart)[0]).not.toBe(run);
    chart.dispose();
  });

  it('the chart keeps its instance and merges a new option without notMerge', async () => {
    const charts: EChartsType[] = [];
    const ready = vi.fn((chart: EChartsType) => {
      charts.push(chart);
      return undefined;
    });
    const view = render(<EChart option={bars([1, 2, 3])} theme="dark" summary="Volume" onReady={ready} />);
    await waitFor(() => expect(ready).toHaveBeenCalledTimes(1));
    const chart = charts[0];
    if (!chart) throw new Error('no chart');
    const set = vi.spyOn(chart, 'setOption');
    const [series] = seriesOf(chart);
    view.rerender(<EChart option={bars([4, 5, 6])} theme="dark" summary="Volume" onReady={ready} />);
    await waitFor(() => expect(ready).toHaveBeenCalledTimes(2));
    expect(charts[1]).toBe(chart);
    expect(set).toHaveBeenCalledTimes(1);
    expect(set.mock.calls[0]?.[1]).not.toHaveProperty('notMerge');
    expect(seriesOf(chart)[0]).toBe(series);
  });
});
