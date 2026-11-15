/**
 * Year by year, from Stats' links: each year's distance, cumulative through it, one line per year. It
 * compares whole years, so it is not under Stats' period; a tap on a year makes it stand out.
 */
import { useState } from 'react';

import { Progress } from '../src/stats/Sections';
import { Screen } from '../src/ui';

export default function YearsScreen() {
  const [selected, setSelected] = useState(() => new Date().getFullYear());
  return (
    <Screen header>
      <Progress selected={selected} onSelect={setSelected} />
    </Screen>
  );
}
