import { describe, expect, it, vi } from 'vitest';

vi.mock('react', () => ({ useSyncExternalStore: () => -1 }));

const { cursorStore } = await import('./cursorStore');

describe('the cursor store', () => {
  it('tells its listeners only when the cursor moves', () => {
    const store = cursorStore();
    const heard = vi.fn();
    const stop = store.subscribe(heard);
    expect(store.get()).toBe(-1);
    store.set(4);
    store.set(4);
    expect(store.get()).toBe(4);
    expect(heard).toHaveBeenCalledTimes(1);
    stop();
    store.set(5);
    expect(heard).toHaveBeenCalledTimes(1);
  });
});
