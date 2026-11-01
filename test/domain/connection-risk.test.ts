import { describe, expect, it } from 'vitest';
import { airportPlaceFor } from '../../src/catalog/airports';
import { assessConnection, assessGap, requiredMinutes, slackMinutes } from '../../src/domain/connection-risk';
import { flight as flightOf, type Flight } from '../../src/domain/model';
import { instant } from '../helpers';

const base = instant('2026-05-14T08:00:00Z');

function flight(
  id: string,
  from: string,
  to: string,
  departMinutes: number,
  blockMinutes: number,
  { carrier = 'BA', departureTerminal = null as string | null, arrivalTerminal = null as string | null } = {}
): Flight {
  const origin = airportPlaceFor(from);
  const destination = airportPlaceFor(to);
  return flightOf({
    id,
    tripId: 'trip',
    carrierCode: carrier,
    flightNumber: '100',
    origin,
    destination,
    startEpochMillis: base + departMinutes * 60_000,
    endEpochMillis: base + (departMinutes + blockMinutes) * 60_000,
    startZoneId: origin.timeZoneId,
    endZoneId: destination.timeZoneId,
    departureTerminal,
    arrivalTerminal
  });
}

describe('ConnectionRisk', () => {
  it('same terminal domestic connection with two hours is comfortable', () => {
    const inbound = flight('a', 'SFO', 'ORD', 0, 240, { arrivalTerminal: '3' });
    const onward = flight('b', 'ORD', 'BOS', 360, 150, { departureTerminal: '3' });
    const verdict = assessConnection(inbound, onward);
    expect(verdict.level).toBe('COMFORTABLE');
    expect(slackMinutes(verdict)).toBeGreaterThan(60);
  });

  it('terminal change on an international arrival needs more time', () => {
    const inbound = flight('a', 'LHR', 'JFK', 0, 480, { arrivalTerminal: '7' });
    const onward = flight('b', 'JFK', 'BOS', 540, 80, { departureTerminal: '8' });
    const [required, reason] = requiredMinutes(inbound, onward);
    expect(required).toBeGreaterThanOrEqual(120);
    expect(reason).toContain('immigration');
  });

  // The same pair of flights, once with a comfortable gap and once with the
  // onward flight moved earlier: shortening the gap has to change the verdict.
  it('a shorter gap degrades the verdict', () => {
    const inbound = flight('a', 'SFO', 'LHR', 0, 600, { arrivalTerminal: '5' });
    const comfortable = assessConnection(
      inbound,
      flight('b', 'LHR', 'FCO', 690, 150, { departureTerminal: '5' })
    );
    expect(comfortable.level).not.toBe('BROKEN');

    const tight = assessConnection(inbound, flight('c', 'LHR', 'FCO', 625, 150, { departureTerminal: '5' }));
    expect(['AT_RISK', 'BROKEN']).toContain(tight.level);
  });

  it('an onward flight that leaves before the inbound lands is broken', () => {
    const inbound = flight('a', 'SFO', 'LHR', 0, 600);
    const onward = flight('b', 'LHR', 'CDG', 500, 80);
    expect(assessConnection(inbound, onward).level).toBe('BROKEN');
  });

  it('changing carrier with checked bags costs extra', () => {
    const inbound = flight('a', 'SFO', 'JFK', 0, 330, { carrier: 'AA', arrivalTerminal: '8' });
    const onward = flight('b', 'JFK', 'LHR', 420, 420, { carrier: 'BA', departureTerminal: '8' });
    const withBags = requiredMinutes(inbound, onward, true)[0];
    const withoutBags = requiredMinutes(inbound, onward, false)[0];
    expect(withBags - withoutBags).toBe(20);
  });

  it('an airport change is judged on the ground transfer', () => {
    const inbound = flight('a', 'SFO', 'LHR', 0, 600);
    const onward = flight('b', 'LGW', 'FCO', 780, 150);
    const [required, reason] = requiredMinutes(inbound, onward);
    expect(required).toBeGreaterThan(120);
    expect(reason.toLowerCase()).toContain('airport change');
  });

  it('ground gaps are judged against the estimate plus a buffer', () => {
    const hotel = airportPlaceFor('LHR');
    const station = airportPlaceFor('LGW');
    expect(assessGap(hotel, station, 20).level).toBe('AT_RISK');
    expect(assessGap(hotel, station, 360).level).toBe('COMFORTABLE');
  });
});
