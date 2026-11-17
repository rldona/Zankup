/**
 * MapLibre GL, loaded on demand with its stylesheet and its worker. MapLibre looks for the worker
 * next to its own file, which the bundler moves: it gets the worker as Vite builds it.
 */
import type { IControl, Map } from 'maplibre-gl';

let loading: Promise<typeof import('maplibre-gl')> | null = null;

export function loadMapLibre() {
  loading ??= Promise.all([
    import('maplibre-gl'),
    import('maplibre-gl/dist/maplibre-gl.css'),
    import('maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'),
  ]).then(([maplibre, , worker]) => {
    maplibre.setWorkerUrl(worker.default);
    return maplibre;
  });
  return loading;
}

export function hasWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(canvas.getContext('webgl2') ?? canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

/** A frame with a dot: the whole route back in view. */
const RESET_ICON =
  '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" ' +
  'stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4' +
  'M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4"/><circle cx="12" cy="12" r="2.5"/></svg>';

/**
 * A button under the zoom ones that takes the map back to where it opened (the whole route, the
 * zone's place), however far it was zoomed or moved.
 */
export function resetControl(label: string, reset: (map: Map) => void): IControl {
  let box: HTMLDivElement | null = null;
  return {
    onAdd(map) {
      box = document.createElement('div');
      box.className = 'maplibregl-ctrl maplibregl-ctrl-group';
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'maplibregl-ctrl-reset';
      button.title = label;
      button.setAttribute('aria-label', label);
      button.innerHTML = RESET_ICON;
      button.addEventListener('click', () => reset(map));
      box.append(button);
      return box;
    },
    onRemove() {
      box?.remove();
      box = null;
    },
  };
}
