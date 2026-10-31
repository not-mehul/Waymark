// Which weather a trip needs: where it is on each day still to come, as runs
// of days in one place, and whether each day can have a forecast yet.

import { MAX_DAYS } from './analytics';
import { formatFixed } from './decimal';
import { tripEndDate, tripStartDate, type Place, type TripDossier, type WeatherBasis } from './model';
import { compareDates, localTime, maxDate, zonedOf, type LocalDate } from './time';
import { whereaboutsAt, zoneIdAt } from './whereabouts';

/** Days, today included, that a forecast reaches. */
export const FORECAST_DAYS = 16;

/** The middle of the day, which is where a day's weather is read from. */
const MIDDAY = localTime(13, 0);

/** A run of consecutive days in one place. */
export interface WeatherStop {
  place: Place;
  days: LocalDate[];
}

/** The place to a hundredth of a degree, about a kilometre: what weather is cached under. */
export function weatherKeyOf(latitude: number, longitude: number): string {
  return `${formatFixed(latitude, 2)},${formatFixed(longitude, 2)}`;
}

export function stopKey(s: WeatherStop): string {
  return weatherKeyOf(s.place.latitude!, s.place.longitude!);
}

export function stopLabel(s: WeatherStop): string {
  return s.place.city.trim() === '' ? s.place.name : s.place.city;
}

/**
 * The trip's remaining days, from [today], grouped by where the trip is at
 * midday on each. Days somewhere with no known position are left out.
 */
export function weatherStops(dossier: TripDossier, today: LocalDate): WeatherStop[] {
  const segments = dossier.segments;
  if (segments.length === 0) return [];
  const first = maxDate(tripStartDate(dossier.trip), today);
  const last = tripEndDate(dossier.trip);
  const stops: WeatherStop[] = [];
  let counted = 0;
  for (let day = first; compareDates(day, last) <= 0 && counted < MAX_DAYS; day = day.add({ days: 1 })) {
    const home = dossier.trip.homeZoneId;
    const zone = zoneIdAt(segments, zonedOf(day, MIDDAY, home).epochMilliseconds, home);
    const where = whereaboutsAt(segments, zonedOf(day, MIDDAY, zone).epochMilliseconds);
    if (where?.latitude == null || where.longitude == null) continue;
    const current = stops[stops.length - 1];
    if (
      current &&
      sameStop(current.place, where) &&
      current.days[current.days.length - 1]!.equals(day.subtract({ days: 1 }))
    ) {
      stops[stops.length - 1] = { ...current, days: [...current.days, day] };
    } else {
      stops.push({ place: where, days: [day] });
    }
    counted++;
  }
  return stops;
}

/** Forecast for the next two weeks, last year's record after that. */
export function basisFor(date: LocalDate, today: LocalDate): WeatherBasis {
  return compareDates(date, today.add({ days: FORECAST_DAYS })) < 0 ? 'FORECAST' : 'LAST_YEAR';
}

function sameStop(a: Place, b: Place): boolean {
  if (a.city.trim() !== '' && b.city.trim() !== '') {
    return (
      a.city.toLowerCase() === b.city.toLowerCase() && a.country.toLowerCase() === b.country.toLowerCase()
    );
  }
  return a.latitude === b.latitude && a.longitude === b.longitude;
}

/** Fahrenheit for the few places that still read it; set from the device's region. */
export const weatherSettings = { fahrenheit: false };

/** The countries whose own weather reports are in Fahrenheit. */
export const FAHRENHEIT_REGIONS: ReadonlySet<string> = new Set([
  'US',
  'BS',
  'BZ',
  'KY',
  'LR',
  'PW',
  'FM',
  'MH'
]);

/** Java's Math.round: half up, toward positive infinity. */
const javaRound = (x: number) => Math.floor(x + 0.5);

export function temperature(celsius: number, fahrenheit: boolean = weatherSettings.fahrenheit): string {
  return fahrenheit ? `${javaRound((celsius * 9) / 5 + 32)}°` : `${javaRound(celsius)}°`;
}

/** The WMO weather code, in words. */
export function describeWeather(code: number): string {
  switch (code) {
    case 0:
      return 'Clear';
    case 1:
      return 'Mostly clear';
    case 2:
      return 'Partly cloudy';
    case 3:
      return 'Overcast';
    case 45:
    case 48:
      return 'Fog';
    case 51:
    case 53:
    case 55:
      return 'Drizzle';
    case 56:
    case 57:
      return 'Freezing drizzle';
    case 61:
      return 'Light rain';
    case 63:
      return 'Rain';
    case 65:
      return 'Heavy rain';
    case 66:
    case 67:
      return 'Freezing rain';
    case 71:
    case 73:
    case 75:
    case 77:
      return 'Snow';
    case 80:
    case 81:
      return 'Showers';
    case 82:
      return 'Heavy showers';
    case 85:
    case 86:
      return 'Snow showers';
    case 95:
      return 'Thunderstorms';
    case 96:
    case 99:
      return 'Thunderstorms, hail';
    default:
      return '—';
  }
}

/** Whether the day is one to pack for: rain, snow or storms. */
export function isWet(code: number): boolean {
  return code >= 51 && code <= 99;
}
