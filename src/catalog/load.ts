// Loads the two large directories (every station, and the world's places) the
// first time something needs them. They ship beside index.html in catalog/,
// not inside the bundle, so the app starts without parsing fourteen megabytes
// of text.

import { AIRPORTS_ASSET, airportDirectoryLoaded, installAirports, parseAirportDirectory } from './airports';
import { PLACES_ASSET, installPlacesInSlices, placesLoaded } from './gazetteer';

let loading: Promise<void> | null = null;

async function text(name: string): Promise<string> {
  const response = await fetch(new URL(`catalog/${name}`, document.baseURI));
  if (!response.ok) throw new Error(`Could not load ${name}: ${response.status}`);
  return response.text();
}

/** Load and install both directories once; later calls share the first. */
export function loadCatalog(): Promise<void> {
  if (airportDirectoryLoaded() && placesLoaded()) return Promise.resolve();
  loading ??= Promise.all([text(AIRPORTS_ASSET), text(PLACES_ASSET)])
    .then(([airports, places]) => {
      installAirports(parseAirportDirectory(airports.split(/\r?\n/)));
      return installPlacesInSlices(places);
    })
    .catch((err: unknown) => {
      loading = null;
      throw err;
    });
  return loading;
}
