import { describe, expect, it } from 'vitest';
import { ideaSections, ideaTally, ideasThatFit, scheduleIdea, tallyTotal } from '../../src/domain/ideas';
import { idea as ideaOf, place, segmentStart, type IdeaKind, type IdeaStatus } from '../../src/domain/model';
import { date, sample, t } from '../helpers';

const bundle = sample();
const ideas = bundle.ideas;
const london = 'Europe/London';
const NOON = t('12:00');

const idea = (title: string, kind: IdeaKind, status: IdeaStatus) =>
  ideaOf({ id: title, tripId: 'trip-sample', title, kind, city: 'London', status });

const minutesOf = (s: { startEpochMillis: number; endEpochMillis: number }) =>
  Math.trunc((s.endEpochMillis - s.startEpochMillis) / 60_000);

describe('IdeaBoard', () => {
  it('sections group by kind and keep a stable order', () => {
    const sections = ideaSections(ideas);
    expect(sections.length).toBeGreaterThan(0);
    const order = sections.map((s) => s.kind);
    expect([...new Set(order)]).toEqual(order);
    for (const section of sections) expect(section.ideas.every((i) => i.kind === section.kind)).toBe(true);
    // Sights come before food, food before walks: the board does not reshuffle.
    expect(order.indexOf('SEE')).toBeLessThan(order.indexOf('EAT'));
  });

  it('open items sort above finished ones', () => {
    const mixed = [
      idea('z-done', 'SEE', 'DONE'),
      idea('a-saved', 'SEE', 'SAVED'),
      idea('m-scheduled', 'SEE', 'SCHEDULED')
    ];
    const sections = ideaSections(mixed);
    expect(sections).toHaveLength(1);
    expect(sections[0]!.ideas.map((i) => i.title)).toEqual(['a-saved', 'm-scheduled', 'z-done']);
  });

  it('dismissed ideas stay out of the way unless asked for', () => {
    const withDismissed = [...ideas, idea('Not this trip', 'SEE', 'DISMISSED')];
    const visible = ideaSections(withDismissed).flatMap((s) => s.ideas);
    expect(visible.some((i) => i.status === 'DISMISSED')).toBe(false);
    const all = ideaSections(withDismissed, { includeDismissed: true }).flatMap((s) => s.ideas);
    expect(all.some((i) => i.status === 'DISMISSED')).toBe(true);
  });

  it('filtering by traveler keeps ideas nobody has claimed', () => {
    const julian = bundle.travelers.find((x) => x.fullName.startsWith('Julian'))!;
    const mara = bundle.travelers.find((x) => x.fullName.startsWith('Mara'))!;
    const forJulian = ideaSections(ideas, { travelerFilter: julian.id }).flatMap((s) => s.ideas);

    // Claimed by Julian: kept. Claimed by Mara alone: dropped. Unclaimed: kept.
    expect(forJulian.some((i) => i.id === 'idea-canal')).toBe(true);
    expect(forJulian.some((i) => i.id === 'idea-rodin')).toBe(false);
    expect(forJulian.some((i) => i.interestedTravelerIds.length === 0)).toBe(true);
    expect(
      ideaSections(ideas, { travelerFilter: mara.id })
        .flatMap((s) => s.ideas)
        .some((i) => i.id === 'idea-rodin')
    ).toBe(true);
  });

  it('the tally counts each status once', () => {
    const tally = ideaTally(ideas);
    expect(tally.saved).toBe(ideas.filter((i) => i.status === 'SAVED').length);
    expect(tally.done).toBe(ideas.filter((i) => i.status === 'DONE').length);
    expect(tallyTotal(tally) + tally.dismissed).toBe(ideas.length);
  });

  it('scheduling produces a segment that carries the idea forward', () => {
    const wallace = ideas.find((i) => i.id === 'idea-wallace')!;
    const segment = scheduleIdea(wallace, 'seg-new', date(2026, 5, 17), t('10:30'), london, ['trav-mara']);

    expect(segment.name).toBe(wallace.title);
    expect(segment.note).toBe(wallace.note);
    expect(segment.startZoneId).toBe('Europe/London');
    expect(segmentStart(segment).hour).toBe(10);
    // The guide says ninety minutes for this one.
    expect(minutesOf(segment)).toBe(90);
    expect(segment.travelerIds).toEqual(['trav-mara']);
  });

  it('an idea with no estimate gets an hour, and an override wins', () => {
    const vague = idea('Something', 'DO', 'SAVED');
    expect(minutesOf(scheduleIdea(vague, 's1', date(2026, 5, 17), NOON, london, []))).toBe(60);
    expect(minutesOf(scheduleIdea(vague, 's2', date(2026, 5, 17), NOON, london, [], 25))).toBe(25);
  });

  it("scheduling falls back to the idea's own interest list for travelers", () => {
    const claimed = { ...idea('Walk', 'SEE', 'SAVED'), interestedTravelerIds: ['trav-julian'] };
    expect(scheduleIdea(claimed, 's3', date(2026, 5, 17), NOON, london, []).travelerIds).toEqual([
      'trav-julian'
    ]);
  });

  it('fitsIn respects the time available including getting there', () => {
    const bloomsbury = place({
      name: 'Hotel',
      city: 'London',
      latitude: 51.5205,
      longitude: -0.1265,
      timeZoneId: 'Europe/London'
    });
    const roomy = ideasThatFit(ideas, bloomsbury, 240);
    const cramped = ideasThatFit(ideas, bloomsbury, 20);
    expect(roomy.length).toBeGreaterThanOrEqual(cramped.length);
    expect(cramped.every((i) => (i.typicalMinutes ?? 60) <= 20)).toBe(true);
  });
});
