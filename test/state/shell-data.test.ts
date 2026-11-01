import { describe, expect, it } from 'vitest';
import { journeyWindows } from '../../src/domain/live-journey';
import { segmentTitle } from '../../src/domain/model';
import { dueReminders, LEAD_OFF, ReminderPreferences, scheduledReminders } from '../../src/domain/reminders';
import { shellSchedule, widgetEntries } from '../../src/state/shell-data';
import { HOUR, flightsIn, sample } from '../helpers';

const dossier = sample();
const segments = dossier.segments;
const first = [...segments].sort((a, b) => a.startEpochMillis - b.startEpochMillis)[0]!;
const on = ReminderPreferences.DEFAULT;

describe('what the shell schedules', () => {
  const before = first.startEpochMillis - 10 * 24 * HOUR;

  it('lists every reminder still to come, soonest first', () => {
    const scheduled = scheduledReminders(segments, on, before);
    expect(scheduled.length).toBeGreaterThan(0);
    const times = scheduled.map((r) => r.fireAtMillis);
    expect(times).toEqual([...times].sort((a, b) => a - b));
    for (const r of scheduled) expect(r.usefulUntilMillis).toBeGreaterThan(r.fireAtMillis);
  });

  it('says what the planner says at the moment each falls due', () => {
    for (const r of scheduledReminders(segments, on, before)) {
      const due = dueReminders(segments, on, r.fireAtMillis).find((d) => d.signature === r.signature);
      expect(due).toBeDefined();
      expect(r.headline).toBe(due!.headline);
      expect(r.detail).toBe(due!.detail);
    }
  });

  it('drops what is no longer worth posting', () => {
    const after = Math.max(...segments.map((s) => s.endEpochMillis));
    expect(scheduledReminders(segments, on, after)).toEqual([]);
  });

  it('has nothing to schedule with reminders off', () => {
    let off = on;
    for (const c of ['FLIGHTS', 'LEAVE', 'CHECK_IN', 'GROUND', 'BOOKINGS', 'STAYS'] as const)
      off = off.with(c, LEAD_OFF);
    expect(shellSchedule(segments, off, before)).toEqual({ reminders: [], journeys: [] });
  });

  it('gives each flight a journey window from three hours out', () => {
    const flight = [...flightsIn(segments)].sort((a, b) => a.startEpochMillis - b.startEpochMillis)[0]!;
    const window = journeyWindows(segments, on, before).find((w) => w.segmentId === flight.id)!;
    expect(window).toMatchObject({
      kind: 'FLIGHT',
      title: segmentTitle(flight),
      showFromMillis: flight.startEpochMillis - 3 * HOUR,
      startMillis: flight.startEpochMillis,
      endMillis: flight.endEpochMillis
    });
  });
});

describe('what the widget lists', () => {
  it('names each booking with its trip, day, clock and place', () => {
    const [entry] = widgetEntries([[dossier.trip, first]]);
    expect(entry).toMatchObject({
      tripId: dossier.trip.id,
      tripName: dossier.trip.name,
      segmentId: first.id,
      title: segmentTitle(first),
      startMillis: first.startEpochMillis
    });
    expect(entry!.day).toMatch(/^\w{3} \d{1,2} \w{3}$/);
    expect(entry!.clock).toMatch(/\d{1,2}:\d{2}/);
  });
});
