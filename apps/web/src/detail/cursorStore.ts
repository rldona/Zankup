/**
 * Where the pointer is along an activity's series (the sample index, null for none), outside React
 * state (OS-1013, as the app's src/detail/cursorStore.ts): the charts and the map write it on every
 * pointer move and the replay on every frame, and only what draws it (the map's dot and the replay's
 * position) listens, so the rest of the activity's page does not redraw as the pointer moves.
 */
import { useSyncExternalStore } from 'react';

export type CursorStore = {
  get: () => number | null;
  set: (index: number | null) => void;
  subscribe: (listener: () => void) => () => void;
};

export function cursorStore(): CursorStore {
  let index: number | null = null;
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
export function useCursor(store: CursorStore): number | null {
  return useSyncExternalStore(store.subscribe, store.get, store.get);
}
