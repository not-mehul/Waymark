// The weather where the trip will be, day by day: kept on the device, and
// brought up to date when the traveler has let the app go online. The
// counterpart of WeatherModel.kt; it lives while the notes screen is open.

import type { Subscription } from 'dexie';
import type { TripDossier } from '../domain/model';
import { systemZone, todayIn } from '../domain/time';
import { weatherStops, type WeatherStop } from '../domain/weather';
import type { StopWeather } from '../online/weather';
import { settings, weather } from './app';

export class WeatherModel {
  stops = $state.raw<StopWeather[]>([]);
  note = $state<string | null>(null);
  updating = $state(false);

  private wanted: WeatherStop[] = [];
  private wantedKey = '';
  private subscription: Subscription | null = null;

  /** Follows the trip: the stops change as bookings do, or as a day passes. */
  track(dossier: TripDossier | null | undefined): void {
    const stops = dossier ? weatherStops(dossier, todayIn(systemZone())) : [];
    const key = JSON.stringify(stops.map((s) => [s.place.latitude, s.place.longitude, s.days.map(String)]));
    if (key === this.wantedKey) return;
    this.wantedKey = key;
    this.wanted = stops;
    this.subscription?.unsubscribe();
    this.subscription = weather.observe(stops).subscribe((list) => (this.stops = list));
  }

  destroy(): void {
    this.subscription?.unsubscribe();
  }

  async refresh(): Promise<void> {
    if (this.wanted.length === 0 || this.updating || !settings.onlineExtras) return;
    this.updating = true;
    try {
      const result = await weather.refresh(
        this.wanted,
        todayIn(systemZone()),
        Date.now(),
        settings.onlineExtras
      );
      this.note =
        result.outcome === 'failed'
          ? `Could not update the weather (${result.reason}). What was fetched before is kept.`
          : null;
    } finally {
      this.updating = false;
    }
  }
}
