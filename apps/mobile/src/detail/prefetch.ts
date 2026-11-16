/**
 * An activity's detail asked for as soon as a finger rests on its card, before the screen opens: the
 * activity, its context (how it compares) and its series all load while the transition runs, instead
 * of one after the other once the screen is there.
 */
import { activityContextQuery, activityQuery } from '@zankup/core';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';

import { useApi } from '../auth';
import { streamsQuery } from '../offline';

export function usePrefetchActivity() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useCallback(
    (id: number) => {
      void queryClient.prefetchQuery(activityContextQuery(api, id));
      queryClient
        .fetchQuery(activityQuery(api, id))
        .then((a) => (a.streams != null ? queryClient.prefetchQuery(streamsQuery(api, id)) : undefined))
        .catch(() => undefined); // the screen asks again and shows what went wrong
    },
    [api, queryClient],
  );
}
