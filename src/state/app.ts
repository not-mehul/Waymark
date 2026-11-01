// The one database and its repositories, for the screens. Tests build their
// own against fake-indexeddb; nothing under src/db reaches for this module.

import { AlertRepository } from '../db/alerts';
import { CostRepository } from '../db/costs';
import { openDatabase } from '../db/database';
import { IdeaRepository } from '../db/ideas';
import { TripRepository } from '../db/trips';
import { Settings } from './settings.svelte';

export const db = openDatabase();
export const trips = new TripRepository(db);
export const ideas = new IdeaRepository(db);
export const costs = new CostRepository(db);
export const alerts = new AlertRepository(db);
export const settings = new Settings();

/** The build's version, from package.json (see vite.config.ts). */
export const APP_VERSION: string = __APP_VERSION__;
