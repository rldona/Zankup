import type { ComponentType } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';

import { Activity } from './routes/Activity';
import { AppError } from './routes/AppError';
import { Feed } from './routes/Feed';
import { Home } from './routes/Home';
import { NotFound } from './routes/NotFound';
import { RequireSession } from './routes/RequireSession';
import { Shell } from './routes/Shell';
import { SignIn } from './routes/SignIn';

/**
 * The screens past the first ones (Home, the feed and an activity, signing in) come in their own
 * files, fetched when first opened, so the app starts with only what its first screen needs; once it
 * is idle, `prefetchScreens` fetches them all, so opening one later does not wait for the network.
 */
const files = {
  analysis: () => import('./routes/Analysis'),
  matches: () => import('./routes/Matches'),
  estimates: () => import('./routes/Estimates'),
  goals: () => import('./routes/Goals'),
  notifications: () => import('./routes/Notifications'),
  weight: () => import('./health/Weight'),
  health: () => import('./routes/Health'),
  profile: () => import('./routes/Profile'),
  progress: () => import('./routes/Progress'),
  zones: () => import('./progress/Zones'),
  fitness: () => import('./progress/Fitness'),
  more: () => import('./progress/More'),
  volume: () => import('./progress/Volume'),
  authorize: () => import('./routes/Authorize'),
  setup: () => import('./routes/Setup'),
  catalogue: () => import('./routes/Catalogue'),
  coach: () => import('./routes/Coach'),
  recover: () => import('./routes/Recover'),
  search: () => import('./routes/Search'),
  settings: () => import('./routes/Settings'),
  library: () => import('./routes/Library'),
  review: () => import('./routes/Review'),
  shared: () => import('./routes/Shared'),
  stats: () => import('./routes/Stats'),
  years: () => import('./routes/Years'),
  sync: () => import('./routes/Sync'),
  training: () => import('./routes/Training'),
};

/** A screen from one of those files, by the name it is exported with. */
function screen<M, K extends keyof M>(load: () => Promise<M>, name: K): Pick<RouteObject, 'lazy'> {
  return { lazy: async () => ({ Component: (await load())[name] as ComponentType }) };
}

/** Fetches every screen's file, once the app has drawn its first one and has nothing else to do. */
export function prefetchScreens() {
  for (const load of Object.values(files)) void load().catch(() => undefined);
}

/** Every screen. Public: sign-in, setup, password recovery and shared activities (/s/…). */
const screens: RouteObject[] = [
  { index: true, element: <Home /> },
  {
    path: 'activities',
    element: <Feed />,
    // An activity opens in the feed's place; its matched activities and its analysis are its own pages.
    children: [
      { path: ':id', element: <Activity /> },
      { path: ':id/matches', ...screen(files.matches, 'Matches') },
      { path: ':id/analysis', ...screen(files.analysis, 'Analysis') },
    ],
  },
  { path: 'goals', ...screen(files.goals, 'Goals') },
  // Progress (OS-1001 to OS-1005): its cards, each opening its page.
  { path: 'progress', ...screen(files.progress, 'Progress') },
  { path: 'progress/volume', ...screen(files.volume, 'VolumePage') },
  { path: 'progress/log', ...screen(files.volume, 'LogPage') },
  { path: 'progress/fitness', ...screen(files.fitness, 'FitnessPage') },
  { path: 'progress/effort', ...screen(files.fitness, 'EffortPage') },
  { path: 'progress/predictions', ...screen(files.fitness, 'PredictionsPage') },
  { path: 'progress/zones', ...screen(files.zones, 'ZonesPage') },
  { path: 'progress/skills', ...screen(files.zones, 'SkillsPage') },
  { path: 'progress/bests', ...screen(files.zones, 'BestsPage') },
  { path: 'progress/monthly', ...screen(files.more, 'MonthlyPage') },
  { path: 'progress/sources', ...screen(files.more, 'SourcesPage') },
  { path: 'estimates', ...screen(files.estimates, 'Estimates') },
  { path: 'notifications', ...screen(files.notifications, 'Notifications') },
  { path: 'search', ...screen(files.search, 'Search') },
  { path: 'stats', ...screen(files.stats, 'Stats') },
  { path: 'stats/calendar', ...screen(files.training, 'TrainingCalendar') },
  { path: 'stats/heatmap', ...screen(files.training, 'Heatmap') },
  { path: 'stats/years', ...screen(files.years, 'Years') },
  { path: 'health', ...screen(files.health, 'Health') },
  { path: 'health/weight', ...screen(files.weight, 'WeightPage') },
  { path: 'coach', ...screen(files.coach, 'Coach') },
  { path: 'routes', ...screen(files.training, 'RoutesList') },
  { path: 'routes/:id', ...screen(files.training, 'RouteView') },
  { path: 'segments', ...screen(files.training, 'SegmentsList') },
  { path: 'segments/:id', ...screen(files.training, 'SegmentView') },
  { path: 'sync', ...screen(files.sync, 'Sync') },
  { path: 'settings', ...screen(files.settings, 'Settings') },
  { path: 'settings/:section', ...screen(files.settings, 'Settings') },
  { path: 'settings/import', ...screen(files.library, 'Import') },
  { path: 'gear', ...screen(files.library, 'Gear') },
  { path: 'review/:period', ...screen(files.review, 'Review') },
  { path: 'review/:period/:value', ...screen(files.review, 'Review') },
  { path: 'catalogue', ...screen(files.catalogue, 'Catalogue') },
  { path: 'profile', ...screen(files.profile, 'Profile') },
  { path: '*', element: <NotFound /> },
];

export const routes: RouteObject[] = [
  {
    errorElement: <AppError />,
    children: [
      { path: '/sign-in', element: <SignIn /> },
      { path: '/setup', ...screen(files.setup, 'Setup') },
      { path: '/forgot', ...screen(files.recover, 'ForgotPassword') },
      { path: '/reset-password', ...screen(files.recover, 'ResetPassword') },
      { path: '/s/:token', ...screen(files.shared, 'Shared') }, // a shared activity: no session (OS-408)
      {
        element: <RequireSession />,
        children: [
          // An AI assistant asking to connect (OAuth): signed in, but outside the app's frame.
          { path: '/oauth/authorize', ...screen(files.authorize, 'Authorize') },
          {
            element: <Shell />,
            // An error in a screen shows inside the frame, with the navigation still there.
            children: [{ errorElement: <AppError />, children: screens }],
          },
        ],
      },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
