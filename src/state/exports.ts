// Hands the trip to whatever the traveler wants to keep it in: a Waymark trip
// file another phone opens back into a whole trip, a calendar file, the
// itinerary as markdown, or printed pages (which the print dialog also saves
// as a PDF). Nothing is uploaded by Waymark itself. Ported from TripExport.kt.

import { mount, unmount } from 'svelte';
import PrintItinerary from '../components/share/PrintItinerary.svelte';
import { tripReport } from '../domain/analytics';
import { buildItineraryDocument } from '../domain/itinerary-document';
import { icsFileName, renderIcs } from '../domain/ics';
import { markdownFileName, renderMarkdown } from '../domain/markdown';
import type { WeatherDay } from '../domain/model';
import { systemZone, todayIn } from '../domain/time';
import { weatherStops } from '../domain/weather';
import { callNative, isNativeShell } from '../platform/native';
import { shareFile } from '../share/files';
import { archiveOf, encodeArchive, MIME } from '../share/trip-archive';
import { weather } from './app';
import type { TripModel } from './trip.svelte';
import { ui } from './ui.svelte';

type Outcome = Awaited<ReturnType<typeof shareFile>>;

/** The whole trip, party, bookings, ideas and costs, for Waymark on another device. */
export function shareTripFile(model: TripModel): Promise<Outcome> | null {
  const d = model.dossier;
  if (!d) return null;
  const text = encodeArchive(
    archiveOf([
      {
        trip: d.trip,
        travelers: d.party.travelers,
        segments: d.segments,
        ideas: model.ideas,
        costs: model.costs.data
      }
    ])
  );
  const name = markdownFileName(d).replace(/\.md$/, '') + '.waymark.json';
  return shareFile(name, text, MIME, d.trip.name);
}

/** Every booking as a calendar event, in UTC, so no calendar can misplace it. */
export function shareCalendar(model: TripModel): Promise<Outcome> | null {
  const d = model.dossier;
  return d ? shareFile(icsFileName(d), renderIcs(d), 'text/calendar', d.trip.name) : null;
}

/** The itinerary as markdown, to read, keep or edit. */
export function shareMarkdown(model: TripModel): Promise<Outcome> | null {
  const d = model.dossier;
  if (!d) return null;
  const text = renderMarkdown({ dossier: d, ideas: model.ideas, analytics: tripReport(d, model.ideas) });
  return shareFile(markdownFileName(d), text, 'text/markdown', d.trip.name);
}

/** The weather already kept for the trip's days, by ISO date; nothing is fetched for it. */
async function keptWeather(model: TripModel): Promise<Map<string, WeatherDay>> {
  const d = model.dossier;
  if (!d) return new Map();
  const stops = await weather.forStops(weatherStops(d, todayIn(systemZone())));
  return new Map(stops.flatMap((s) => s.days.map((day) => [day.date.toString(), day] as const)));
}

/** The itinerary on A4 pages, through the print dialog, which also saves a PDF. */
export async function printItinerary(model: TripModel): Promise<void> {
  const d = model.dossier;
  if (!d) return;
  const blocks = buildItineraryDocument({
    dossier: d,
    costs: model.costs.data.expenses.length > 0 ? model.costs.summary : null,
    weather: await keptWeather(model)
  });
  const host = document.createElement('div');
  host.className = 'print-host';
  document.body.append(host);
  const view = mount(PrintItinerary, { target: host, props: { blocks, footer: `${d.trip.name} · Waymark` } });
  document.body.classList.add('printing');
  const done = () => {
    document.body.classList.remove('printing');
    void unmount(view);
    host.remove();
    window.removeEventListener('afterprint', done);
  };
  if (isNativeShell()) {
    // The WebView has no print dialog of its own: the shell hands this page,
    // laid out for print, to Android's, and answers once the job is spooled.
    await callNative('print', { name: d.trip.name }).catch((err: unknown) => {
      console.error('Print', err);
      ui.tell('The itinerary could not be printed.');
    });
    done();
    return;
  }
  window.addEventListener('afterprint', done);
  window.print();
}
