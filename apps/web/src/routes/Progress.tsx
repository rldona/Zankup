/**
 * Progress (OS-1001), as the app's tab and Strava's: Volume first, Series beside the Training log, then
 * Fitness, Relative effort, race predictions, Goals, Training zones, Skills, Best times, Monthly
 * activities and Data sources. One column on a phone, a grid from the desktop; each card opens its page
 * under /progress (Goals opens /goals).
 */
import { t } from '../i18n';
import { BestsCard, SkillsCard, ZonesCard } from '../progress/Zones';
import { EffortCard, FitnessCard, PredictionsCard } from '../progress/Fitness';
import { GoalsCard, MonthlyCard, SourcesCard } from '../progress/More';
import { LogCard, SeriesCard, VolumeCard } from '../progress/Volume';

export function Progress() {
  return (
    <section className="page progress">
      <div className="titlebar">
        <h1>{t('nav.progress')}</h1>
      </div>
      <div className="pgrid">
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
