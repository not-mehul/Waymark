// Every town of a thousand people or more, every region, and the landmarks,
// islands, mountains, lakes, parks and stations a traveler names, each with
// the time zone it keeps. The list is the shared one in dusk-dawn (GeoNames,
// CC BY 4.0), so Waymark and Ledger put the same place in the same spot.
// Matching is exact — on the name, or on one of the names the place is known
// by — and never a substring, so "Manhattan" is not an airfield in Kansas.

import { parsePlaces, parsePlacesInSlices, type Gazetteer, type WorldPlace } from 'dusk-dawn/places';
import { place, type LatLon, type Place } from '../domain/model';
import { countryForCode } from './countries';

export type { WorldPlace };

/** Shipped beside index.html as catalog/places.txt by vite.config.ts. */
export const PLACES_ASSET = 'places.txt';

let world: Gazetteer | null = null;

export function placesLoaded(): boolean {
  return world !== null;
}

/** Install the gazetteer from its text, in one go (tests). */
export function installPlaces(text: string): void {
  world = parsePlaces(text);
}

/** Install the gazetteer a slice at a time, so the shelf keeps scrolling while it loads. */
export async function installPlacesInSlices(text: string): Promise<void> {
  world = await parsePlacesInSlices(text);
}

/** How many places are installed. */
export function placeCount(): number {
  return world?.size ?? 0;
}

/** Every place called exactly [name], or also known by it, the one most people mean first. */
export function placesNamed(name: string): WorldPlace[] {
  return world?.named(name) ?? [];
}

export function positionOfWorldPlace(p: WorldPlace): LatLon {
  return { latitude: p.latitude, longitude: p.longitude };
}

/** Countries go by the name the country table gives them, as stations do. */
export function countryNameOf(p: WorldPlace): string {
  return countryForCode(p.country)?.name ?? p.countryName;
}

/**
 * A booking or idea at [p]; [label] is what the traveler typed, kept as the
 * name. A landmark or neighbourhood belongs to its city, so ideas group under
 * Paris, not under the Eiffel Tower.
 */
export function worldPlaceToPlace(p: WorldPlace, label: string = p.name): Place {
  return place({
    name: label,
    city: p.city || p.name,
    country: countryNameOf(p),
    latitude: p.latitude,
    longitude: p.longitude,
    timeZoneId: p.timeZoneId
  });
}
