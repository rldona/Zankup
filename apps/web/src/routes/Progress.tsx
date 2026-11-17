/**
 * Progress (OS-1001), as the app's tab and Strava's: Volume first, Series beside the Training log, then
 * Fitness, Relative effort, race predictions, Goals, Training zones, Skills, Best times, Monthly
 * activities and Data sources. One column on a phone, a grid from the desktop; each card opens its page
 * under /progress (Goals opens /goals).
 */
import type { PointerEvent } from 'react';

import { t } from '../i18n';
import { BestsCard, SkillsCard, ZonesCard } from '../progress/Zones';
import { EffortCard, FitnessCard, PredictionsCard } from '../progress/Fitness';
import { GoalsCard, MonthlyCard, SourcesCard } from '../progress/More';
import { LogCard, SeriesCard, VolumeCard } from '../progress/Volume';

/** The cards that open something: the same shade on hover and the same ripple (index.css). */
const TAPPABLE = '.fxcard, .morecard, .vlogcard, .vseries, .pz-card, .pk-card, .pb-card';

/** Android's press ripple on the card pressed: a circle from the pointer that grows until it covers it. */
function ripple(event: PointerEvent<HTMLElement>) {
  if (event.button !== 0) return;
  const card = (event.target as HTMLElement).closest<HTMLElement>(TAPPABLE);
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

export function Progress() {
  return (
    <section className="page progress">
      <div className="titlebar">
        <h1>{t('nav.progress')}</h1>
      </div>
      <div className="pgrid" onPointerDown={ripple}>
        <div className="pwide">
          <VolumeCard />
        </div>
        <div className="ppair">
          <SeriesCard />
          <LogCard />
        </div>
        <FitnessCard />
        <EffortCard />
        <PredictionsCard />
        <GoalsCard />
        <ZonesCard />
        <SkillsCard />
        <BestsCard />
        <MonthlyCard />
        <SourcesCard />
      </div>
    </section>
  );
}
