import {
  aiStatusQuery,
  MATERIAL_ICONS,
  type MaterialIcon as MaterialIconName,
  type MessageKey,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent, type RefObject } from 'react';
import { Link, Outlet, useLocation, useNavigationType } from 'react-router';

import { api } from '../api';
import { LatestActivity } from '../LatestActivity';
import { MaterialIcon } from '../MaterialIcon';
import { t } from '../i18n';
import { Mark } from '../Mark';
import { useSync } from '../sync';
import { SyncNow } from '../syncSheet';
import { useOnline } from '../offline';
import { SyncProgress } from '../SyncProgress';
import { useServerLanguage, useServerTheme } from '../theme';

type Item = { to: string; icon: MaterialIconName; label: MessageKey };

/**
 * The sections, as the app's tabs (OS-903, OS-1001): Home, Activities, Progress, Health and You, in the
 * rail from 600 px and the tab bar below it. Goals live in Progress.
 */
const SECTIONS: Item[] = [
  { to: '/', icon: 'home', label: 'nav.home' },
  { to: '/activities', icon: 'cards_stack', label: 'nav.feed' },
  { to: '/progress', icon: 'show_chart', label: 'nav.progress' },
  { to: '/health', icon: 'favorite', label: 'nav.health' },
  { to: '/profile', icon: 'person', label: 'nav.you' },
];

/**
 * The rail has room for one more than the app's tab bar: the training calendar, under Health, as
 * another way to see the activities. On a phone it stays in the Activities header.
 */
const CALENDAR: Item = { to: '/stats/calendar', icon: 'calendar_month', label: 'stats.calendar' };
const RAIL: Item[] = [...SECTIONS.slice(0, 4), CALENDAR, ...SECTIONS.slice(4)];

/**
 * The foot of the rail: what the app reaches from Home's header (the Coach, the notifications) and from
 * You (Settings). On a phone they stay where the app has them.
 */
const SHORTCUTS: (Item & { owns: RegExp })[] = [
  { to: '/coach', icon: 'wand_stars', label: 'nav.coach', owns: /^\/coach(\/|$)/ },
  { to: '/notifications', icon: 'notifications', label: 'home.notifications', owns: /^\/notifications$/ },
  { to: '/settings', icon: 'settings', label: 'nav.settings', owns: /^\/settings(\/|$)/ },
];

/** Where each section's screens live, so it stays marked on them (an activity under Activities…). */
const OWNS: Record<string, RegExp> = {
  '/': /^\/(notifications|coach)?$/,
  '/activities': /^\/(activities|search|sync)(\/|$)/,
  '/health': /^\/health(\/|$)/,
  '/progress': /^\/(progress|goals)(\/|$)/,
  '/stats/calendar': /^\/stats\/calendar$/,
  '/profile': /^\/(profile|stats|estimates|gear|settings|review|routes|segments)(\/|$)/,
};

/** Screens pushed over a section, as the app's: on a phone they have no tab bar. */
const PUSHED = /^\/activities\/\d+/;

const sectionOf = (pathname: string) => SECTIONS.find((s) => OWNS[s.to]?.test(pathname));
/** In the rail the calendar is its own section, checked before You (which owns the rest of Stats). */
const railSectionOf = (pathname: string) => RAIL.find((s) => OWNS[s.to]?.test(pathname));
const shortcutOf = (pathname: string) => SHORTCUTS.find((s) => s.owns.test(pathname));

/** An icon, filled while it is the current one, as on the app. */
function NavIcon({ icon, on }: { icon: MaterialIconName; on: boolean }) {
  const filled = `${icon}-fill`;
  return <MaterialIcon name={on && filled in MATERIAL_ICONS ? (filled as MaterialIconName) : icon} />;
}

/** A section with its label under the icon, or (at the rail's foot) the icon alone. */
function NavLink({ item, current, bare }: { item: Item; current: string | undefined; bare?: boolean }) {
  const on = item.to === current;
  return (
    <Link
      to={item.to}
      className={on ? 'on' : undefined}
      aria-current={on ? 'page' : undefined}
      aria-label={bare ? t(item.label) : undefined}
      title={bare ? t(item.label) : undefined}
    >
      <NavIcon icon={item.icon} on={on} />
      {!bare && <span>{t(item.label)}</span>}
    </Link>
  );
}

/**
 * Where each screen leads back to, above its title, and where that link shows: the screen it was
 * opened from (Stats for its calendar, heatmap, reviews, routes and segments; Settings for its
 * sections; You for Stats, Estimates and Gear). `show` keeps it to the sizes where nothing else on
 * screen leads back: a phone (the rail names the Coach, the notifications and Settings) or below 1024 px
 * (Settings' list sits beside its sections from there). An activity has its own way back, in the feed.
 */
const PARENTS: {
  match: RegExp;
  to: string;
  label: MessageKey;
  show?: 'phone' | 'compact';
}[] = [
  { match: /^\/(search|sync)$/, to: '/activities', label: 'nav.feed' },
  { match: /^\/(progress\/.|goals$)/, to: '/progress', label: 'nav.progress' },
  { match: /^\/(coach|notifications)$/, to: '/', label: 'nav.home', show: 'phone' },
  { match: /^\/settings\/import$/, to: '/settings', label: 'nav.settings' },
  { match: /^\/settings\/./, to: '/settings', label: 'nav.settings', show: 'compact' },
  { match: /^\/settings$/, to: '/profile', label: 'nav.you', show: 'phone' },
  // The calendar is a section of the rail from 600 px: the way back to Stats only on a phone.
  { match: /^\/stats\/calendar$/, to: '/stats', label: 'nav.stats', show: 'phone' },
  { match: /^\/(stats\/|review\/|routes$|segments$)/, to: '/stats', label: 'nav.stats' },
  { match: /^\/routes\/./, to: '/routes', label: 'routes.title' },
  { match: /^\/segments\/./, to: '/segments', label: 'segments.title' },
  { match: /^\/(stats|estimates|gear)$/, to: '/profile', label: 'nav.you' },
  { match: /^\/health\/weight$/, to: '/health', label: 'nav.health' },
];

function Back({ pathname }: { pathname: string }) {
  const parent = PARENTS.find((p) => p.match.test(pathname));
  if (!parent) return null;
  return (
    <div className={`crumbrow show-${parent.show ?? 'always'}`}>
      <Link to={parent.to} className="crumb">
        <MaterialIcon name="chevron_left" />
        {t(parent.label)}
      </Link>
    </div>
  );
}

/**
 * The signed-in frame (OS-202), in three sizes as the app (docs/prototypes/web): below 600 px the
 * app's bottom tab bar and one header per screen; from 600 px a narrow rail with the sections on top and
 * the Coach, the notifications and Settings at its foot; from 1200 px the content centred at one width.
 * Safe areas respected.
 */
/** Android's press ripple on a card that opens something (`.tap`): a circle from the pointer that grows until it covers it. */
function ripple(event: PointerEvent<HTMLElement>) {
  if (event.button !== 0) return;
  const card = (event.target as HTMLElement).closest<HTMLElement>('.tap');
  if (!card || !event.currentTarget.contains(card)) return;
  const box = card.getBoundingClientRect();
  const x = event.clientX - box.left;
  const y = event.clientY - box.top;
  const size = 2 * Math.hypot(Math.max(x, box.width - x), Math.max(y, box.height - y));
  const wave = document.createElement('span');
  wave.className = 'ripple';
  wave.setAttribute('aria-hidden', 'true');
  Object.assign(wave.style, { left: `${x}px`, top: `${y}px`, width: `${size}px`, height: `${size}px` });
  wave.addEventListener('animationend', () => wave.remove());
  card.append(wave);
}

/** The parts of the page that scroll: the column, the Activities list and an open activity. */
const SCROLLERS = ['', '.feedcol', '.detailcol'] as const;
type Positions = Partial<Record<(typeof SCROLLERS)[number], number>>;
/** Where each visited screen was left, by its history entry, so going back returns there. */
const left = new Map<string, Positions>();

function scrollerOf(main: HTMLElement, name: (typeof SCROLLERS)[number]) {
  return name ? main.querySelector<HTMLElement>(name) : main;
}

/**
 * A new screen opens at its top; going back to a screen, by the browser's back or by a back link to
 * the screen just left, returns to where it was left, so leaving a list for one of its cards and
 * coming back keeps the place. A screen still drawing its figures is not tall enough at once: the
 * place is tried again each frame for a moment, until it is reached or the person scrolls.
 */
function useScrollMemory(main: RefObject<HTMLElement | null>) {
  const location = useLocation();
  const type = useNavigationType();
  const { key } = location;
  const path = location.pathname + location.search;
  const here = useRef({ key, path });
  const before = useRef<{ key: string; path: string } | null>(null);

  useEffect(() => {
    const column = main.current;
    if (!column) return;
    const save = (event: Event) => {
      const target = event.target as HTMLElement;
      const name = SCROLLERS.find((n) => (n ? target.matches?.(n) : target === column));
      if (name === undefined) return;
      const at = here.current.key;
      left.set(at, { ...left.get(at), [name]: target.scrollTop });
    };
    column.addEventListener('scroll', save, { capture: true, passive: true });
    // A click can navigate before the browser has dispatched the scroll that set the place (a programmatic
    // click in the tests, a very fast tap): save every scroller before the route changes.
    const remember = () => {
      for (const name of SCROLLERS) {
        const box = scrollerOf(column, name);
        if (box) left.set(here.current.key, { ...left.get(here.current.key), [name]: box.scrollTop });
      }
    };
    document.addEventListener('click', remember, true);
    return () => {
      column.removeEventListener('scroll', save, { capture: true });
      document.removeEventListener('click', remember, true);
    };
  }, [main]);

  useLayoutEffect(() => {
    if (here.current.key === key) return;
    const back = type === 'POP' ? key : before.current?.path === path ? before.current.key : null;
    const saved = back ? left.get(back) : undefined;
    before.current = here.current;
    here.current = { key, path };
    const column = main.current;
    if (!column) return;
    const place = () => {
      let reached = true;
      for (const name of SCROLLERS) {
        const box = scrollerOf(column, name);
        // A new screen at its top; the Activities list keeps its own place while a card is open.
        if (!box || (!saved && name === '.feedcol')) continue;
        const top = saved?.[name] ?? 0;
        box.scrollTop = top;
        if (Math.abs(box.scrollTop - top) > 1) reached = false;
      }
      if (saved) left.set(key, saved);
      return reached;
    };
    if (place() || !saved) return;
    const until = performance.now() + 1500;
    let frame = requestAnimationFrame(function again() {
      if (!place() && performance.now() < until) frame = requestAnimationFrame(again);
    });
    const stop = () => cancelAnimationFrame(frame);
    const events = ['wheel', 'touchstart', 'pointerdown', 'keydown'] as const;
    events.forEach((e) => column.addEventListener(e, stop, { passive: true, once: true }));
    return () => {
      stop();
      events.forEach((e) => column.removeEventListener(e, stop));
    };
  }, [key, path, type, main]);
}

export function Shell() {
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  useScrollMemory(main);
  useServerTheme();
  useServerLanguage();
  const online = useOnline();
  const sync = useSync();
  const ai = useQuery({ ...aiStatusQuery(api), retry: false });
  const coach = !!(ai.data?.available && ai.data.enabled);
  const [sheet, setSheet] = useState(false);
  const syncNow = () => {
    setSheet(true);
    if (!sync.running) sync.start.mutate(false);
  };
  const shortcut = shortcutOf(pathname)?.to;
  const section = sectionOf(pathname)?.to;
  const railSection = railSectionOf(pathname)?.to;

  return (
    <SyncNow.Provider value={{ start: syncNow, running: sync.running }}>
      <div className={`app${PUSHED.test(pathname) ? ' pushed' : ''}`}>
        <aside className="rail">
          <Link to="/" className="brand" aria-label="Zankup">
            <Mark height="2rem" />
          </Link>
          <nav aria-label={t('nav.main')}>
            {RAIL.map((item) => (
              <NavLink key={item.to} item={item} current={shortcut ? undefined : railSection} />
            ))}
          </nav>
          <div className="grow" />
          <div className="railfoot">
            {SHORTCUTS.filter((s) => coach || s.to !== '/coach').map((item) => (
              <NavLink key={item.to} item={item} current={shortcut} bare />
            ))}
          </div>
        </aside>

        <LatestActivity />

        <main className="main" ref={main} onPointerDown={ripple}>
          {!online && (
            <p className="offline" role="status">
              {t('offline.notice')}
            </p>
          )}
          <Back pathname={pathname} />
          <Outlet />
        </main>

        {sheet && (sync.latest || sync.start.isError) && (
          <div className="sheet" role="dialog" aria-label={t('sync.title')}>
            {sync.start.isError ? (
              <p role="alert">{sync.start.error.message}</p>
            ) : (
              sync.latest && <SyncProgress run={sync.latest} />
            )}
            <div className="actions">
              <Link to="/sync" onClick={() => setSheet(false)}>
                {t('sync.recent')}
              </Link>
              <button type="button" onClick={() => setSheet(false)}>
                {t('sync.close')}
              </button>
            </div>
          </div>
        )}

        <nav className="tabbar" aria-label={t('nav.main')}>
          {SECTIONS.map((item) => (
            <NavLink key={item.to} item={item} current={section} />
          ))}
        </nav>
      </div>
    </SyncNow.Provider>
  );
}
