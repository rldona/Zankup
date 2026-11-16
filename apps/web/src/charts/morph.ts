/**
 * How a chart's new option reaches ECharts so its bars and lines slide from the old values to the new
 * ones (OS-1012, as the app's #317) instead of growing again from the floor.
 *
 * `notMerge` (what the charts used to get) throws every series away and draws the new ones from zero.
 * Merging keeps them, and ECharts animates the change, but a merge also keeps whatever the old option
 * had and the new one leaves out. So each component gets an id that holds its shape (the keys it
 * sets, and its `type`): the same shape merges into the one on screen and morphs, where every key it
 * had is set again; another shape gets another id and is drawn afresh; and with `replaceMerge` a
 * component the new option no longer has goes away. A series is known by its name when it has one
 * (a sport, a year), so the others keep their place when one comes or goes.
 */

type Json = Record<string, unknown>;

const isObject = (v: unknown): v is Json => typeof v === 'object' && v !== null && !Array.isArray(v);

/** The components Zankup's charts set (each one an object or a list of them). */
const COMPONENTS = new Set([
  'series',
  'xAxis',
  'yAxis',
  'grid',
  'legend',
  'tooltip',
  'toolbox',
  'brush',
  'title',
  'dataZoom',
  'visualMap',
  'graphic',
]);

/** The keys an option sets, nested, with its `type`: lists (the data) and functions are leaves. */
function shape(value: unknown): string {
  if (!isObject(value)) return '';
  return Object.keys(value)
    .sort()
    .map((k) => {
      const v = value[k];
      return k === 'type' && typeof v === 'string' ? `type=${v}` : isObject(v) ? `${k}{${shape(v)}}` : k;
    })
    .join(',');
}

/** A short, stable digest of a shape. */
function digest(text: string): string {
  let h = 5381;
  for (let i = 0; i < text.length; i++) h = Math.imul(h, 33) ^ text.charCodeAt(i);
  return (h >>> 0).toString(36);
}

/** The option with an id on every component that has none (see above). */
export function withIds<T extends Json>(option: T): T {
  const out: Json = { ...option };
  for (const key of Object.keys(option)) {
    const value = option[key];
    if (!COMPONENTS.has(key) || value == null) continue;
    const list = Array.isArray(value) ? value : [value];
    const used = new Set<string>();
    out[key] = list.map((item: unknown, i) => {
      if (!isObject(item)) return item;
      if (item.id != null) {
        used.add(String(item.id));
        return item;
      }
      const name = key === 'series' && item.name != null ? String(item.name) : String(i);
      let id = `${key}:${name}:${digest(shape(item))}`;
      if (used.has(id)) id = `${id}:${i}`;
      used.add(id);
      return { ...item, id };
    });
  }
  return out as T;
}

/** The global keys (not components): when they change shape, nothing on screen is kept. */
const globals = (option: Json) =>
  Object.keys(option)
    .filter((k) => !COMPONENTS.has(k))
    .sort()
    .map((k) => `${k}{${shape(option[k])}}`)
    .join('|');

/**
 * How to set `next` over `previous` (both with their ids, null for a chart just drawn): merged, with
 * every component type either has replaced by id, or from scratch when the global keys differ.
 */
export function morphOptions(
  previous: Json | null,
  next: Json,
): { notMerge: true } | { replaceMerge: string[] } {
  if (!previous || globals(previous) !== globals(next)) return { notMerge: true };
  const types = new Set(
    [...Object.keys(previous), ...Object.keys(next)].filter(
      (k) => COMPONENTS.has(k) && (previous[k] != null || next[k] != null),
    ),
  );
  return { replaceMerge: [...types] };
}
