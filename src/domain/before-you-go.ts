// What a trip asks of its travelers before they leave: a packing list to
// start from, and the passports and visas that run out too soon.

import { Temporal } from 'temporal-polyfill';
import { countryOf } from '../catalog/countries';
import { parseDate, type LocalDate } from './time';
import {
  displayName,
  tripEndDate,
  tripStartDate,
  type IdentityDoc,
  type Traveler,
  type TripDossier
} from './model';
import { destinations } from './time-at-place';

/** Lines to start a list from, worked out from where the trip goes. */
export function suggestedChecklist(dossier: TripDossier): string[] {
  const lines = ['Passport', 'Travel insurance', 'Phone charger', 'Medication'];
  const home = dossier.segments[0] ? countryOf(dossier.segments[0].origin) : null;
  const abroad = destinations(dossier)
    .map((s) => countryOf(s.place))
    .filter((c) => c !== null && c.code !== home?.code);
  const plugs = new Set(abroad.flatMap((c) => c!.plugTypes));
  const homePlugs = new Set(home?.plugTypes ?? []);
  const needed = [...plugs].filter((p) => !homePlugs.has(p)).sort();
  if (needed.length > 0) lines.push(`Plug adapter (Type ${needed.join(' / ')})`);
  const currencies = [...new Set(abroad.map((c) => c!.currencyCode))].filter((c) => c !== home?.currencyCode);
  if (currencies.length > 0) lines.push(`Some ${currencies.join(' and ')} in cash`);
  if (dossier.segments.some((s) => s.kind === 'FLIGHT')) lines.push('Check in online');
  lines.push('Offline maps downloaded');
  return lines;
}

export type DocProblem = 'EXPIRED_BEFORE' | 'EXPIRES_DURING' | 'UNDER_SIX_MONTHS';

export interface DocWarning {
  traveler: Traveler;
  doc: IdentityDoc;
  problem: DocProblem;
  expires: LocalDate;
}

/** Many countries turn a passport away with under six months left on it. */
const SIX_MONTHS = { months: 6 } as const;

/**
 * Every passport or visa on the party that runs out before the trip starts,
 * while it is under way, or (for passports) within six months of the end.
 */
export function documentWarnings(dossier: TripDossier): DocWarning[] {
  const start = tripStartDate(dossier.trip);
  const end = tripEndDate(dossier.trip);
  const out: DocWarning[] = [];
  for (const traveler of dossier.party.travelers) {
    for (const doc of traveler.identityDocs) {
      const expires = parseDate(doc.expires);
      if (!expires) continue;
      let problem: DocProblem | null = null;
      if (compare(expires, start) < 0) problem = 'EXPIRED_BEFORE';
      else if (compare(expires, end) < 0) problem = 'EXPIRES_DURING';
      else if (doc.kind === 'PASSPORT' && compare(expires, end.add(SIX_MONTHS)) < 0)
        problem = 'UNDER_SIX_MONTHS';
      if (problem) out.push({ traveler, doc, problem, expires });
    }
  }
  return out;
}

const compare = (a: LocalDate, b: LocalDate) => Temporal.PlainDate.compare(a, b);

export function warningText(w: DocWarning): string {
  const label = w.doc.label.trim();
  const what = label === '' || /^(passport|visa)$/i.test(label) ? w.doc.kind.toLowerCase() : label;
  const whose = `${displayName(w.traveler)}'s ${what}`;
  switch (w.problem) {
    case 'EXPIRED_BEFORE':
      return `${whose} runs out before the trip starts.`;
    case 'EXPIRES_DURING':
      return `${whose} runs out during the trip.`;
    case 'UNDER_SIX_MONTHS':
      return `${whose} has under six months left after the trip. Many countries ask for six.`;
  }
}
