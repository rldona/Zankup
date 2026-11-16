/**
 * An activity's page asked for as soon as the pointer rests on its card or a day that holds it, a
 * finger touches it or the keyboard reaches it (OS-1013, as the app's #320): the activity, its context
 * (how it compares) and then its series load while the click happens, instead of one after the other
 * once the page is there.
 */
import { activityContextQuery, activityQuery, streamsQuery } from '@zankup/core';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef } from 'react';

import { api } from '../api';

/** How long the pointer rests on a card before its activity is fetched: passing over it does not. */
export const REST_MS = 120;

export function usePrefetchActivity() {
  const queryClient = useQueryClient();
  return useCallback(
    (id: number) => {
      void queryClient.prefetchQuery(activityContextQuery(api, id));
      queryClient
        .fetchQuery(activityQuery(api, id))
        .then((a) => (a.streams != null ? queryClient.prefetchQuery(streamsQuery(api, id)) : undefined))
        .catch(() => undefined); // the page asks again and shows what went wrong
    },
    [queryClient],
  );
}

type Intent = {
  onPointerEnter: (event: { pointerType: string }) => void;
  onPointerLeave: () => void;
  onTouchStart: () => void;
  onFocus: () => void;
};

/**
 * The handlers that prefetch these activities on intent: the pointer resting `REST_MS` on the element,
 * a touch (at once: the tap follows) or the focus. Spread them on the card's link.
 */
export function useActivityIntent(): (ids: number | readonly number[]) => Intent {
  const prefetch = usePrefetchActivity();
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  return useCallback(
    (ids) => {
      const all = typeof ids === 'number' ? [ids] : ids;
      const now = () => all.forEach(prefetch);
      return {
        onPointerEnter: (event) => {
          if (event.pointerType === 'touch' || !all.length) return; // a touch has its own, at once
          clearTimeout(timer.current);
          timer.current = setTimeout(now, REST_MS);
        },
        onPointerLeave: () => clearTimeout(timer.current),
        onTouchStart: now,
        onFocus: now,
      };
    },
    [prefetch],
  );
}
