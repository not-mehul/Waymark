// Daily weather from Open-Meteo, which needs no account and no key: the
// forecast for the next two weeks, and the record for the same dates a year
// ago for anything further out. Kept in IndexedDB so it reads abroad without
// a signal, and fetched only when online extras are on. Ported from
// Weather.kt.

import { liveQuery, type Observable } from 'dexie';
import type { WaymarkDb, WeatherRow } from '../db/database';
import { formatFixed } from '../domain/decimal';
import type { WeatherBasis, WeatherDay } from '../domain/model';
import { compareDates, parseDate, type LocalDate } from '../domain/time';
import { basisFor, stopKey, type WeatherStop } from '../domain/weather';
import { fetchText, type FetchText } from './rates';

const FORECAST = 'https://api.open-meteo.com/v1/forecast';
const ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
/** A forecast is fetched again after three hours. */
export const FORECAST_TTL_MILLIS = 3 * 60 * 60 * 1000;

export class WeatherError extends Error {}

const place = (latitude: number, longitude: number) =>
  `latitude=${formatFixed(latitude, 2)}&longitude=${formatFixed(longitude, 2)}`;

export function forecastUrl(latitude: number, longitude: number, from: LocalDate, to: LocalDate): string {
  return (
    `${FORECAST}?${place(latitude, longitude)}&start_date=${from.toString()}&end_date=${to.toString()}&timezone=auto` +
    '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max'
  );
}

export function archiveUrl(latitude: number, longitude: number, from: LocalDate, to: LocalDate): string {
  return (
    `${ARCHIVE}?${place(latitude, longitude)}&start_date=${from.toString()}&end_date=${to.toString()}&timezone=auto` +
    '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum'
  );
}

/** The days in a reply, dated as the reply dates them. Days with no reading are left out. */
export function parseWeather(text: string, basis: WeatherBasis): WeatherDay[] {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch {
    throw new WeatherError('The weather service replied with something unreadable');
  }
  if (typeof root !== 'object' || root === null) throw new WeatherError('Not a weather reply');
  const reply = root as { reason?: unknown; daily?: Record<string, unknown> };
  if (reply.reason !== undefined) throw new WeatherError(String(reply.reason));
  const daily = reply.daily;
  if (!daily || typeof daily !== 'object') throw new WeatherError('No daily weather in the reply');
  const dates = daily['time'];
  if (!Array.isArray(dates)) throw new WeatherError('No dates in the reply');
  const column = (name: string): unknown[] => (Array.isArray(daily[name]) ? (daily[name] as unknown[]) : []);
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : null);
  const [codes, highs, lows, chances, sums] = [
    'weather_code',
    'temperature_2m_max',
    'temperature_2m_min',
    'precipitation_probability_max',
    'precipitation_sum'
  ].map(column) as [unknown[], unknown[], unknown[], unknown[], unknown[]];
  return dates.flatMap((d, i): WeatherDay[] => {
    const date = parseDate(typeof d === 'string' ? d : null);
    const code = num(codes[i]);
    const highC = num(highs[i]);
    const lowC = num(lows[i]);
    if (!date || code === null || highC === null || lowC === null) return [];
    const chance = num(chances[i]);
    return [
      {
        date,
        code: Math.trunc(code),
        highC,
        lowC,
        rainChance: chance === null ? null : Math.trunc(chance),
        rainMm: num(sums[i]),
        basis
      }
    ];
  });
}

const row = (key: string, day: WeatherDay, fetchedAtMillis: number): WeatherRow => ({
  placeKey: key,
  date: day.date.toString(),
  code: day.code,
  highC: day.highC,
  lowC: day.lowC,
  rainChance: day.rainChance,
  rainMm: day.rainMm,
  basis: day.basis,
  fetchedAtMillis
});

const dayOf = (r: WeatherRow): WeatherDay | null => {
  const date = parseDate(r.date);
  return date
    ? {
        date,
        code: r.code,
        highC: r.highC,
        lowC: r.lowC,
        rainChance: r.rainChance,
        rainMm: r.rainMm,
        basis: r.basis
      }
    : null;
};

/** A stop and what is known of its days' weather. */
export interface StopWeather {
  stop: WeatherStop;
  days: WeatherDay[];
}

export type WeatherOutcome =
  { outcome: 'updated' } | { outcome: 'notAllowed' } | { outcome: 'failed'; reason: string };

export class WeatherRepository {
  constructor(
    private readonly db: WaymarkDb,
    private readonly get: FetchText = fetchText
  ) {}

  async forStops(stops: readonly WeatherStop[]): Promise<StopWeather[]> {
    const out: StopWeather[] = [];
    for (const stop of stops) {
      const wanted = new Set(stop.days.map((d) => d.toString()));
      const rows = this.db.weather
        .where('[placeKey+date]')
        .between(
          [stopKey(stop), stop.days[0]!.toString()],
          [stopKey(stop), stop.days.at(-1)!.toString()],
          true,
          true
        );
      const days = (await rows.toArray())
        .filter((r) => wanted.has(r.date))
        .map(dayOf)
        .filter((d): d is WeatherDay => d !== null)
        .sort((a, b) => compareDates(a.date, b.date));
      out.push({ stop, days });
    }
    return out;
  }

  observe(stops: readonly WeatherStop[]): Observable<StopWeather[]> {
    return liveQuery(() => this.forStops(stops));
  }

  /** Fetch what the stops are missing: a forecast older than three hours, last year's record once. */
  async refresh(
    stops: readonly WeatherStop[],
    today: LocalDate,
    nowMillis: number,
    allowed: boolean
  ): Promise<WeatherOutcome> {
    if (!allowed) return { outcome: 'notAllowed' };
    await this.db.weather.where('date').below(today.toString()).delete();
    try {
      for (const stop of stops) await this.refreshStop(stop, today, nowMillis);
      return { outcome: 'updated' };
    } catch (error) {
      return { outcome: 'failed', reason: error instanceof WeatherError ? error.message : 'No connection' };
    }
  }

  private async refreshStop(stop: WeatherStop, today: LocalDate, nowMillis: number): Promise<void> {
    const key = stopKey(stop);
    const { latitude, longitude } = stop.place as { latitude: number; longitude: number };
    const soon = stop.days.filter((d) => basisFor(d, today) === 'FORECAST');
    const later = stop.days.filter((d) => basisFor(d, today) === 'LAST_YEAR');
    const cached = new Map(
      (await this.db.weather.where('placeKey').equals(key).toArray()).map((r) => [r.date, r] as const)
    );

    const stale = soon.some((day) => {
      const r = cached.get(day.toString());
      return !r || r.basis !== 'FORECAST' || nowMillis - r.fetchedAtMillis > FORECAST_TTL_MILLIS;
    });
    if (stale && soon.length > 0) {
      const days = parseWeather(
        await this.get(forecastUrl(latitude, longitude, soon[0]!, soon.at(-1)!)),
        'FORECAST'
      );
      await this.db.weather.bulkPut(days.map((d) => row(key, d, nowMillis)));
    }

    const missing = later.filter((d) => !cached.has(d.toString()));
    if (missing.length > 0) {
      const record = new Map(
        parseWeather(
          await this.get(
            archiveUrl(
              latitude,
              longitude,
              missing[0]!.subtract({ years: 1 }),
              missing.at(-1)!.subtract({ years: 1 })
            )
          ),
          'LAST_YEAR'
        ).map((d) => [d.date.toString(), d] as const)
      );
      // Each trip day takes the reading from a year before it; the 29th of February takes the 28th's.
      const days = missing.flatMap((day) => {
        const reading = record.get(day.subtract({ years: 1 }).toString());
        return reading ? [{ ...reading, date: day }] : [];
      });
      await this.db.weather.bulkPut(days.map((d) => row(key, d, nowMillis)));
    }
  }
}
