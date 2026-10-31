// The trip as a calendar file (RFC 5545), one event per booking. Times are in
// UTC, which every calendar reads correctly; each UID is the booking's id, so
// importing the same trip twice updates the events rather than doubling them.

import { markdownFileName } from './markdown';
import {
  chronological,
  designator,
  destinationOf,
  displayName,
  nights,
  partyById,
  segmentTitle,
  shortLabel,
  type Segment,
  type TripDossier
} from './model';

export function icsFileName(dossier: TripDossier): string {
  return markdownFileName(dossier).replace(/\.md$/, '') + '.ics';
}

export function renderIcs(dossier: TripDossier, nowMillis: number = Date.now()): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Waymark//Itinerary//EN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${icsText(dossier.trip.name)}`
  ];
  for (const s of chronological(dossier.segments)) {
    lines.push(
      'BEGIN:VEVENT',
      `UID:${s.id}@waymark`,
      `DTSTAMP:${utc(nowMillis)}`,
      `DTSTART:${utc(s.startEpochMillis)}`,
      `DTEND:${utc(s.endEpochMillis)}`,
      `SUMMARY:${icsText(summary(s))}`
    );
    const where = location(s);
    if (where !== null) lines.push(`LOCATION:${icsText(where)}`);
    const about = description(s, dossier);
    if (about.trim() !== '') lines.push(`DESCRIPTION:${icsText(about)}`);
    lines.push('END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  // CRLF line endings and 75-octet folding are what the format requires.
  return lines.map(fold).join('\r\n') + '\r\n';
}

function summary(s: Segment): string {
  switch (s.kind) {
    case 'FLIGHT':
      return `${designator(s)} ${shortLabel(s.origin)} → ${shortLabel(destinationOf(s))}`;
    case 'LODGING':
      return s.propertyName;
    case 'GROUND':
      return segmentTitle(s);
    case 'EXPERIENCE':
      return s.name;
  }
}

function location(s: Segment): string | null {
  const p = s.origin;
  const parts = [
    ...new Set([p.name.trim() === '' ? null : p.name, p.address].filter((x): x is string => x != null))
  ];
  const text = parts.join(', ');
  return text.trim() === '' ? null : text;
}

function description(s: Segment, dossier: TripDossier): string {
  const lines: string[] = [];
  if (s.confirmationCode != null) {
    lines.push(`Reference: ${s.confirmationCode}` + (s.bookedWith != null ? ` (${s.bookedWith})` : ''));
  }
  switch (s.kind) {
    case 'FLIGHT':
      if (s.departureTerminal != null) lines.push(`Terminal ${s.departureTerminal}`);
      if (s.cabin != null) lines.push(s.cabin);
      for (const [travelerId, seat] of Object.entries(s.seats)) {
        const who = partyById(dossier.party, travelerId);
        lines.push(`${who ? displayName(who) : 'Seat'}: ${seat}`);
      }
      break;
    case 'LODGING': {
      const n = nights(s);
      lines.push(`${n} night` + (n === 1 ? '' : 's'));
      if (s.roomDescription != null) lines.push(s.roomDescription);
      if (s.checkInNote != null) lines.push(s.checkInNote);
      break;
    }
    case 'GROUND':
      if (s.provider != null) lines.push(s.provider);
      if (s.pickupInstruction != null) lines.push(s.pickupInstruction);
      break;
    case 'EXPERIENCE':
      if (s.curatedBy != null) lines.push(s.curatedBy);
      break;
  }
  if (s.note != null) lines.push(s.note);
  return lines.join('\n');
}

/** yyyyMMdd'T'HHmmss'Z' */
function utc(millis: number): string {
  return new Date(millis)
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}/, '');
}

/** TEXT values escape backslash, semicolon, comma and newline. */
export function icsText(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r\n/g, '\\n')
    .replace(/\n/g, '\\n');
}

const encoder = new TextEncoder();

/** Lines longer than 75 octets continue after a single space; never splitting a character. */
export function fold(line: string): string {
  if (encoder.encode(line).length <= 75) return line;
  let out = '';
  let width = 0;
  let limit = 75;
  for (const c of line) {
    const bytes = encoder.encode(c).length;
    if (width + bytes > limit) {
      out += '\r\n ';
      width = 0;
      limit = 74;
    }
    out += c;
    width += bytes;
  }
  return out;
}
