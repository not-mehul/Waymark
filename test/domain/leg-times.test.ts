import { describe, expect, it } from 'vitest';
import { endAfter, flightLeg, isOrdered, legDayOffset, legMinutes } from '../../src/domain/leg-times';
import { Temporal, zonedOf } from '../../src/domain/time';
import { date as d, t } from '../helpers';

const date = d(2026, 11, 10);
const SFO = 'America/Los_Angeles';
const LAX = SFO;
const SIN = 'Asia/Singapore';
const HKG = 'Asia/Hong_Kong';
const LHR = 'Europe/London';
const AMS = 'Europe/Amsterdam';
const SYD = 'Australia/Sydney';

const dateTime = (day: Temporal.PlainDate, time: string) => day.toPlainDateTime(t(time));

describe('LegTimes', () => {
  it('an arrival two calendar days later is found, not saved negative', () => {
    // SQ33: the departure that used to save at minus seven hours.
    const leg = flightLeg(date, t('21:45'), SFO, t('06:30'), SIN)!;
    expect(legDayOffset(leg)).toBe(2);
    expect(legMinutes(leg)).toBe(16 * 60 + 45);
  });

  it('an arrival the day before departure is found', () => {
    // CX880 leaves Hong Kong after midnight and lands in Los Angeles the previous evening.
    const leg = flightLeg(date, t('00:50'), HKG, t('22:15'), LAX)!;
    expect(legDayOffset(leg)).toBe(-1);
    expect(legMinutes(leg)).toBe(13 * 60 + 25);
  });

  it('overnight and same-day flights keep their ordinary shape', () => {
    const transatlantic = flightLeg(date, t('16:20'), SFO, t('10:55'), LHR)!;
    expect(legDayOffset(transatlantic)).toBe(1);
    expect(legMinutes(transatlantic)).toBe(10 * 60 + 35);

    const hop = flightLeg(date, t('07:05'), LHR, t('09:25'), AMS)!;
    expect(legDayOffset(hop)).toBe(0);
    expect(legMinutes(hop)).toBe(80);

    // Westbound across the date line: same calendar date, fifteen hours.
    const pacific = flightLeg(date, t('10:45'), SYD, t('06:35'), LAX)!;
    expect(legDayOffset(pacific)).toBe(0);
    expect(legMinutes(pacific)).toBe(14 * 60 + 50);
  });

  it('a stated day offset is used as stated, and refused when impossible', () => {
    expect(flightLeg(date, t('21:45'), SFO, t('06:30'), SIN, 2)).not.toBeNull();
    expect(flightLeg(date, t('21:45'), SFO, t('06:30'), SIN, 0)).toBeNull();
  });

  it('times that cannot be a flight are refused', () => {
    expect(flightLeg(date, t('09:00'), LHR, t('09:00'), LHR, 0)).toBeNull();
    // A day and a half in the air is a time typed against the wrong airport.
    expect(flightLeg(date, t('09:00'), LHR, t('21:00'), LHR, 1)).toBeNull();
  });

  it('an end before its start runs past midnight once, and no further', () => {
    const start = zonedOf(date, t('22:40'), LHR);
    const sleeper = endAfter(start, dateTime(date, '07:10'), LHR);
    expect(sleeper.toPlainDate()).toEqual(date.add({ days: 1 }));
    expect(isOrdered(start, sleeper)).toBe(true);

    const backwards = endAfter(start, dateTime(date.subtract({ days: 3 }), '07:10'), LHR);
    expect(backwards.toPlainDate()).toEqual(date.subtract({ days: 3 }));
    expect(isOrdered(start, backwards)).toBe(false);
  });
});
