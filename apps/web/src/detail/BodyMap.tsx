import {
  BACK,
  BODY_DETAIL as DETAIL,
  BODY_SHAPES as SHAPES,
  BODY_SILHOUETTE as SILHOUETTE,
  FRONT,
  type Muscle,
} from '@zankup/core';
import { useId } from 'react';

import { t } from '../i18n';

const label = (side: 'front' | 'back', worked: Map<Muscle, number>) =>
  `${t(side === 'front' ? 'strength.front' : 'strength.back')}: ${
    (side === 'front' ? FRONT : BACK)
      .filter((m) => worked.has(m))
      .map((m) => t(`muscle.${m}`))
      .join(', ') || '—'
  }`;

/**
 * The glow of a worked muscle: brighter in its middle (each shape's own box, objectBoundingBox). Each
 * figure has its own id: `url(#…)` takes the first element with it in the page, and when that one is in
 * a hidden figure (the feed beside the detail) the glow paints nothing.
 */
/** A per-figure gradient id that is safe inside `url(#…)` (React's ids carry «»). */
const useGlowId = () => `muscle-glow-${useId().replace(/[^\w-]/g, '')}`;

const Glow = ({ id }: { id: string }) => (
  <defs>
    <radialGradient id={id}>
      <stop offset="0" className="glow-core" />
      <stop offset="1" className="glow-edge" />
    </radialGradient>
  </defs>
);

/** One side of the figure, its muscles shaded by how many sets worked them. */
function Side({
  side,
  worked,
  glow,
  x = 0,
}: {
  side: 'front' | 'back';
  worked: Map<Muscle, number>;
  glow: string;
  x?: number;
}) {
  const most = Math.max(1, ...worked.values());
  return (
    <g transform={x ? `translate(${x} 0)` : undefined}>
      <path d={SILHOUETTE} className="body" />
      {DETAIL[side].map((d) => (
        <path key={d} d={d} className="muscle" />
      ))}
      {Object.entries(SHAPES[side]).map(([muscle, paths]) =>
        paths.map((d, i) => {
          const sets = worked.get(muscle as Muscle) ?? 0;
          return (
            <path
              key={`${muscle}-${i}`}
              d={d}
              className={sets ? 'muscle on' : 'muscle'}
              style={sets ? { opacity: 0.45 + 0.55 * (sets / most), fill: `url(#${glow})` } : undefined}
            />
          );
        }),
      )}
    </g>
  );
}

/** The front and back figures with the muscles worked: in the strength panel, or as the cover. */
export function BodyMap({ worked }: { worked: Map<Muscle, number> }) {
  const glow = useGlowId();
  return (
    <div className="bodymap">
      {(['front', 'back'] as const).map((side) => (
        <figure key={side}>
          <svg viewBox="0 0 100 200" role="img" aria-label={label(side, worked)}>
            <Glow id={`${glow}${side}`} />
            <Side side={side} worked={worked} glow={`${glow}${side}`} />
          </svg>
          <figcaption className="note">{t(side === 'front' ? 'strength.front' : 'strength.back')}</figcaption>
        </figure>
      ))}
    </div>
  );
}

/**
 * A strength workout's cover (as the map is a route's): the body map across the top of the detail,
 * with the muscles worked named under it.
 */
export function MuscleCover({ worked }: { worked: Map<Muscle, number> }) {
  return (
    <section className="mapblock musclecover" aria-label={t('strength.muscles')}>
      <BodyMap worked={worked} />
      <p className="coverlegend">
        <span className="swatch" aria-hidden="true" />
        {[...worked.keys()].map((m) => t(`muscle.${m}`)).join(' · ')}
      </p>
    </section>
  );
}

/** Both sides small, on a feed card's tile in place of a route. */
export function BodyThumb({ worked }: { worked: Map<Muscle, number> }) {
  const glow = useGlowId();
  return (
    <svg className="thumb bodythumb" viewBox="-30 -4 270 208" aria-hidden="true">
      <Glow id={glow} />
      <Side side="front" worked={worked} glow={glow} />
      <Side side="back" worked={worked} glow={glow} x={110} />
    </svg>
  );
}
