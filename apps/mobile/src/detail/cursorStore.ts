/**
 * Where the finger is along an activity's series (the sample index, -1 for none), outside React state:
 * the charts write it every frame while the finger moves, and only what draws it (the dot on the map)
 * listens, so the rest of the activity's screen does not redraw sixty times a second.
 */
import { useSyncExternalStore } from 'react';

export type CursorStore = {
  get: () => number;
  set: (index: number) => void;
  subscribe: (listener: () => void) => () => void;
};

export function cursorStore(): CursorStore {
  let index = -1;
  const listeners = new Set<() => void>();
  return {
    get: () => index,
    set: (next) => {
      if (next === index) return;
      index = next;
      listeners.forEach((listener) => listener());
    },
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

/** The cursor's index, redrawing the component that reads it when it moves. */
export function useCursor(store: CursorStore): number {
  return useSyncExternalStore(store.subscribe, store.get);
}
