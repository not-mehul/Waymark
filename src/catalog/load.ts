// Loads the two large directories (every station, every city) the first time
// something needs them. They ship beside index.html in catalog/, not inside
// the bundle, so the app starts without parsing two megabytes of text.

import { AIRPORTS_ASSET, airportDirectoryLoaded, installAirports, parseAirportDirectory } from './airports';
import { CITIES_ASSET, citiesLoaded, installCities, parseCityDirectory } from './cities';

let loading: Promise<void> | null = null;

async function lines(name: string): Promise<string[]> {
  const response = await fetch(new URL(`catalog/${name}`, document.baseURI));
  if (!response.ok) throw new Error(`Could not load ${name}: ${response.status}`);
  return (await response.text()).split(/\r?\n/);
}

/** Load and install both directories once; later calls share the first. */
export function loadCatalog(): Promise<void> {
  if (airportDirectoryLoaded() && citiesLoaded()) return Promise.resolve();
  loading ??= Promise.all([lines(AIRPORTS_ASSET), lines(CITIES_ASSET)])
    .then(([airports, cities]) => {
      installAirports(parseAirportDirectory(airports));
      installCities(parseCityDirectory(cities));
    })
    .catch((err: unknown) => {
      loading = null;
      throw err;
    });
  return loading;
}
