/** Volume, Series and the Training log (OS-1002). TO BUILD. */
import { t } from '../i18n';
import { ProgressCardStub, ProgressPageStub } from './stub';

export function VolumeCard() {
  return <ProgressCardStub title={t('nav.progress')} />;
}

export function VolumePage() {
  return <ProgressPageStub title={t('nav.progress')} />;
}

export function SeriesCard() {
  return <ProgressCardStub title={t('nav.progress')} />;
}

export function LogCard() {
  return <ProgressCardStub title={t('nav.progress')} />;
}

export function LogPage() {
  return <ProgressPageStub title={t('nav.progress')} />;
}
