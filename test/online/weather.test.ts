import 'fake-indexeddb/auto';
import { describe, expect, it } from 'vitest';
import { openDatabase } from '../../src/db/database';
import { place } from '../../src/domain/model';
import { localDate, type LocalDate } from '../../src/domain/time';
import type { WeatherStop } from '../../src/domain/weather';
import { FORECAST_TTL_MILLIS, parseWeather, WeatherError, WeatherRepository } from '../../src/online/weather';

const today = localDate(2026, 5, 1);
const lisbon = place({ name: 'Lisbon', city: 'Lisbon', country: 'PT', latitude: 38.72, longitude: -9.14 });
/** Three days in the forecast's reach, two beyond it. */
const stop: WeatherStop = { place: lisbon, days: [3, 4, 5, 20, 21].map((n) => today.add({ days: n })) };

function reply(days: LocalDate[], archive: boolean): string {
  const extra = archive
    ? { precipitation_sum: days.map(() => 0) }
    : { precipitation_probability_max: days.map(() => 10) };
  return JSON.stringify({
    daily: {
      time: days.map(String),
      weather_code: days.map(() => 1),
      temperature_2m_max: days.map(() => 20),
      temperature_2m_min: days.map(() => 12),
      ...extra
    }
  });
}

function service() {
  const asked: string[] = [];
  const get = (url: string) => {
    asked.push(url);
    const archive = url.includes('archive-api');
    const days = archive ? stop.days.slice(3).map((d) => d.subtract({ years: 1 })) : stop.days.slice(0, 3);
    return Promise.resolve(reply(days, archive));
  };
  return { asked, get };
}

describe('parseWeather', () => {
  it('reads a day only when it has a code and both temperatures', () => {
    const text = JSON.stringify({
      daily: {
        time: ['2026-05-04', '2026-05-05'],
        weather_code: [61, null],
        temperature_2m_max: [18.5, 19],
        temperature_2m_min: [11, 12],
        precipitation_probability_max: [70, 5]
      }
    });
    expect(parseWeather(text, 'FORECAST')).toEqual([
      {
        date: localDate(2026, 5, 4),
        code: 61,
        highC: 18.5,
        lowC: 11,
        rainChance: 70,
        rainMm: null,
        basis: 'FORECAST'
      }
    ]);
  });

  it('turns the service’s own complaint into the reason', () => {
    expect(() => parseWeather('{"error":true,"reason":"Latitude out of range"}', 'FORECAST')).toThrow(
      'Latitude out of range'
    );
    expect(() => parseWeather('nope', 'FORECAST')).toThrow(WeatherError);
  });
});

describe('WeatherRepository', () => {
  it('asks nobody when online extras are off', async () => {
    const { asked, get } = service();
    const repo = new WeatherRepository(openDatabase('weather-off'), get);
    expect(await repo.refresh([stop], today, 0, false)).toEqual({ outcome: 'notAllowed' });
    expect(asked).toEqual([]);
  });

  it('fills the next days from the forecast and the rest from last year, and keeps both', async () => {
    const { asked, get } = service();
    const repo = new WeatherRepository(openDatabase('weather-both'), get);
    expect(await repo.refresh([stop], today, 0, true)).toEqual({ outcome: 'updated' });
    expect(asked).toHaveLength(2);
    const [kept] = await repo.forStops([stop]);
    expect(kept!.days.map((d) => d.date)).toEqual(stop.days);
    expect(kept!.days.map((d) => d.basis)).toEqual([
      'FORECAST',
      'FORECAST',
      'FORECAST',
      'LAST_YEAR',
      'LAST_YEAR'
    ]);
  });

  it('does not fetch a fresh forecast or last year’s record again', async () => {
    const { asked, get } = service();
    const repo = new WeatherRepository(openDatabase('weather-again'), get);
    await repo.refresh([stop], today, 0, true);
    await repo.refresh([stop], today, 60_000, true);
    expect(asked).toHaveLength(2);
    // Three hours on, only the forecast is asked for again.
    await repo.refresh([stop], today, FORECAST_TTL_MILLIS + 60_000, true);
    expect(asked).toHaveLength(3);
    expect(asked.at(-1)).not.toContain('archive-api');
  });

  it('keeps what it had when the service cannot be reached', async () => {
    const repo = new WeatherRepository(openDatabase('weather-down'), () =>
      Promise.reject(new TypeError('offline'))
    );
    expect(await repo.refresh([stop], today, 0, true)).toEqual({
      outcome: 'failed',
      reason: 'No connection'
    });
  });
});
