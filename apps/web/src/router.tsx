import { createBrowserRouter, type RouteObject } from 'react-router';

import { Activity } from './routes/Activity';
import { Analysis } from './routes/Analysis';
import { Matches } from './routes/Matches';
import { AppError } from './routes/AppError';
import { Feed } from './routes/Feed';
import { Estimates } from './routes/Estimates';
import { Goals } from './routes/Goals';
import { Notifications } from './routes/Notifications';
import { Home } from './routes/Home';
import { Health } from './routes/Health';
import { NotFound } from './routes/NotFound';
import { Profile } from './routes/Profile';
import { Progress } from './routes/Progress';
import { BestsPage, SkillsPage, ZonesPage } from './progress/Zones';
import { EffortPage, FitnessPage, PredictionsPage } from './progress/Fitness';
import { MonthlyPage, SourcesPage } from './progress/More';
import { LogPage, VolumePage } from './progress/Volume';
import { Authorize } from './routes/Authorize';
import { RequireSession } from './routes/RequireSession';
import { Setup } from './routes/Setup';
import { Shell } from './routes/Shell';
import { SignIn } from './routes/SignIn';
import { Catalogue } from './routes/Catalogue';
import { Coach } from './routes/Coach';
import { ForgotPassword, ResetPassword } from './routes/Recover';
import { Search } from './routes/Search';
import { Settings } from './routes/Settings';
import { Gear, Import } from './routes/Library';
import { Review } from './routes/Review';
import { Shared } from './routes/Shared';
import { Stats } from './routes/Stats';
import { Sync } from './routes/Sync';
import {
  Heatmap,
  RoutesList,
  RouteView,
  SegmentsList,
  SegmentView,
  TrainingCalendar,
} from './routes/Training';

/** Every screen. Public: sign-in, setup, password recovery and shared activities (/s/…). */
const screens: RouteObject[] = [
  { index: true, element: <Home /> },
  {
    path: 'activities',
    element: <Feed />,
    // An activity opens in the feed's place; its matched activities and its analysis are its own pages.
    children: [
      { path: ':id', element: <Activity /> },
      { path: ':id/matches', element: <Matches /> },
      { path: ':id/analysis', element: <Analysis /> },
    ],
  },
  { path: 'goals', element: <Goals /> },
  // Progress (OS-1001 to OS-1005): its cards, each opening its page.
  { path: 'progress', element: <Progress /> },
  { path: 'progress/volume', element: <VolumePage /> },
  { path: 'progress/log', element: <LogPage /> },
  { path: 'progress/fitness', element: <FitnessPage /> },
  { path: 'progress/effort', element: <EffortPage /> },
  { path: 'progress/predictions', element: <PredictionsPage /> },
  { path: 'progress/zones', element: <ZonesPage /> },
  { path: 'progress/skills', element: <SkillsPage /> },
  { path: 'progress/bests', element: <BestsPage /> },
  { path: 'progress/monthly', element: <MonthlyPage /> },
  { path: 'progress/sources', element: <SourcesPage /> },
  { path: 'estimates', element: <Estimates /> },
  { path: 'notifications', element: <Notifications /> },
  { path: 'search', element: <Search /> },
  { path: 'stats', element: <Stats /> },
  { path: 'stats/calendar', element: <TrainingCalendar /> },
  { path: 'stats/heatmap', element: <Heatmap /> },
  { path: 'health', element: <Health /> },
  { path: 'coach', element: <Coach /> },
  { path: 'routes', element: <RoutesList /> },
  { path: 'routes/:id', element: <RouteView /> },
  { path: 'segments', element: <SegmentsList /> },
  { path: 'segments/:id', element: <SegmentView /> },
  { path: 'sync', element: <Sync /> },
  { path: 'settings', element: <Settings /> },
  { path: 'settings/:section', element: <Settings /> },
  { path: 'settings/import', element: <Import /> },
  { path: 'gear', element: <Gear /> },
  { path: 'review/:period', element: <Review /> },
  { path: 'review/:period/:value', element: <Review /> },
  { path: 'catalogue', element: <Catalogue /> },
  { path: 'profile', element: <Profile /> },
  { path: '*', element: <NotFound /> },
];

export const routes: RouteObject[] = [
  {
    errorElement: <AppError />,
    children: [
      { path: '/sign-in', element: <SignIn /> },
      { path: '/setup', element: <Setup /> },
      { path: '/forgot', element: <ForgotPassword /> },
      { path: '/reset-password', element: <ResetPassword /> },
      { path: '/s/:token', element: <Shared /> }, // a shared activity: no session (OS-408)
      {
        element: <RequireSession />,
        children: [
          // An AI assistant asking to connect (OAuth): signed in, but outside the app's frame.
          { path: '/oauth/authorize', element: <Authorize /> },
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
