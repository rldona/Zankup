/**
 * Tabs start at the top, as Strava's: a tab keeps its screen mounted (and its scroll), so pressing a tab
 * scrolls its screen back to the top before it shows; pressing the tab already open scrolls up animated.
 * Each tab's scrolling list registers here under the tab's name; app/(tabs)/_layout.tsx calls it.
 */
import { useEffect, type RefObject } from 'react';

type Scrollable = {
  scrollTo?: (options: { x?: number; y?: number; animated?: boolean }) => void;
  scrollToOffset?: (options: { offset: number; animated?: boolean }) => void;
};

const tops = new Map<string, (animated: boolean) => void>();

export function useScrollTop(tab: string | undefined, ref: RefObject<Scrollable | null>) {
  useEffect(() => {
    if (!tab) return;
    const top = (animated: boolean) => {
      const list = ref.current;
      if (list?.scrollToOffset) list.scrollToOffset({ offset: 0, animated });
      else list?.scrollTo?.({ y: 0, animated });
    };
    tops.set(tab, top);
    return () => {
      if (tops.get(tab) === top) tops.delete(tab);
    };
  }, [tab, ref]);
}

export function scrollTabToTop(tab: string, animated: boolean) {
  tops.get(tab)?.(animated);
}
