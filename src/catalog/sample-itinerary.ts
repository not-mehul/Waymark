// A worked example, so every feature has a real case to render: a shared
// outbound, an itinerary that splits for a day, a train across a border,
// with booking references on it. Fictional travelers, real stations,
// plausible times. The domain tests run against it.

import { flightLeg } from '../domain/leg-times';
import {
  experience as experienceSegment,
  flight,
  ground,
  idea as makeIdea,
  lodging,
  place,
  segmentEnd,
  traveler,
  trip as makeTrip,
  type Experience,
  type Idea,
  type IdeaKind,
  type Place,
  type Segment,
  type Traveler,
  type Trip
} from '../domain/model';
import { localTime, zonedOf, type LocalDate, type LocalTime } from '../domain/time';
import { airlineName } from './airlines';
import { airportPlaceFor } from './airports';

export const SAMPLE_TRIP_NAME = 'London & Paris';

export interface SampleBundle {
  trip: Trip;
  travelers: Traveler[];
  segments: Segment[];
  ideas: Idea[];
}

const LONDON = 'Europe/London';
const PARIS = 'Europe/Paris';
const PACIFIC = 'America/Los_Angeles';

const bloomsbury = place({
  name: 'The Bloomsbury Rooms',
  city: 'London',
  country: 'United Kingdom',
  latitude: 51.5205,
  longitude: -0.1265,
  timeZoneId: LONDON,
  address: 'Montague Street, Bloomsbury, London WC1B'
});
const stPancras = place({
  name: 'St Pancras International',
  code: 'QQS',
  city: 'London',
  country: 'United Kingdom',
  latitude: 51.5308,
  longitude: -0.1261,
  timeZoneId: LONDON,
  address: 'Euston Road, London N1C'
});
const gareDuNord = place({
  name: 'Gare du Nord',
  code: 'XPG',
  city: 'Paris',
  country: 'France',
  latitude: 48.8809,
  longitude: 2.3553,
  timeZoneId: PARIS,
  address: '18 Rue de Dunkerque, 75010 Paris'
});
const saintJacques = place({
  name: 'Hôtel Saint-Jacques',
  city: 'Paris',
  country: 'France',
  latitude: 48.8462,
  longitude: 2.3466,
  timeZoneId: PARIS,
  address: '35 Rue des Écoles, 75005 Paris'
});
const soane = place({
  name: "Sir John Soane's Museum",
  city: 'London',
  country: 'United Kingdom',
  latitude: 51.517,
  longitude: -0.117,
  timeZoneId: LONDON,
  address: "13 Lincoln's Inn Fields, London WC2A"
});
const borough = place({
  name: 'Borough Market',
  city: 'London',
  country: 'United Kingdom',
  latitude: 51.5055,
  longitude: -0.091,
  timeZoneId: LONDON
});
const orangerie = place({
  name: "Musée de l'Orangerie",
  city: 'Paris',
  country: 'France',
  latitude: 48.8638,
  longitude: 2.3226,
  timeZoneId: PARIS,
  address: 'Jardin des Tuileries, 75001 Paris'
});
const baratin = place({
  name: 'Le Baratin',
  city: 'Paris',
  country: 'France',
  latitude: 48.8722,
  longitude: 2.3877,
  timeZoneId: PARIS,
  address: '3 Rue Jouye-Rouve, 75020 Paris'
});

const MINUTE = 60_000;
const at = (date: LocalDate, hour: number, minute: number, zone: string) =>
  zonedOf(date, localTime(hour, minute), zone).epochMilliseconds;

/** [departure] is the local date the party leaves San Francisco. */
export function buildSampleItinerary(departure: LocalDate): SampleBundle {
  const tripId = 'trip-sample';
  const mara = traveler({
    id: 'trav-mara',
    fullName: 'Mara Ellison',
    nickname: 'Mara',
    seatPreference: 'WINDOW',
    mealPreference: 'Vegetarian',
    contactPhone: '+1 415 555 0148'
  });
  const julian = traveler({
    id: 'trav-julian',
    fullName: 'Julian Beck',
    nickname: 'Julian',
    seatPreference: 'AISLE',
    contactPhone: '+1 415 555 0172'
  });
  const party = [mara.id, julian.id];

  // BA286 as published: 16:20 from San Francisco, 10:55 the next morning
  // into Heathrow, through the same arithmetic the Add flight screen uses.
  const outboundLeg = flightLeg(departure, localTime(16, 20), PACIFIC, localTime(10, 55), LONDON);
  if (!outboundLeg) throw new Error('BA286 does not fit');
  const outbound = flight({
    id: 'seg-outbound',
    tripId,
    carrierCode: 'BA',
    flightNumber: '286',
    origin: airportPlaceFor('SFO'),
    destination: airportPlaceFor('LHR'),
    startEpochMillis: outboundLeg.departure.epochMilliseconds,
    endEpochMillis: outboundLeg.arrival.epochMilliseconds,
    startZoneId: PACIFIC,
    endZoneId: LONDON,
    travelerIds: party,
    confirmationCode: 'K7QH2P',
    bookedWith: airlineName('BA'),
    ticketNumbers: { [mara.id]: '125-2364119807', [julian.id]: '125-2364119808' },
    departureTerminal: 'I',
    departureGate: 'A6',
    arrivalTerminal: '5',
    aircraft: 'Airbus A350-1000',
    cabin: 'Economy',
    seats: { [mara.id]: '21A', [julian.id]: '21C' }
  });
  const landed = segmentEnd(outbound).toPlainDate();

  const hotelLondon = lodging({
    id: 'seg-hotel-london',
    tripId,
    propertyName: bloomsbury.name,
    origin: bloomsbury,
    startEpochMillis: at(landed, 15, 0, LONDON),
    endEpochMillis: at(landed.add({ days: 4 }), 11, 0, LONDON),
    startZoneId: LONDON,
    endZoneId: LONDON,
    travelerIds: party,
    confirmationCode: 'BLM-4471-QE',
    bookedWith: 'Direct booking',
    roomDescription: 'Two rooms, second floor, garden side',
    checkInNote: 'Reception is staffed from 07:00. Late arrivals collect a key from the porter.'
  });

  const airportTrain = ground({
    id: 'seg-heathrow-express',
    tripId,
    mode: 'TRAIN',
    origin: airportPlaceFor('LHR'),
    destination: stPancras,
    startEpochMillis: outbound.endEpochMillis + 55 * MINUTE,
    endEpochMillis: outbound.endEpochMillis + 110 * MINUTE,
    startZoneId: LONDON,
    endZoneId: LONDON,
    travelerIds: party,
    provider: 'Elizabeth line',
    pickupInstruction: 'Platform level, follow signs from Terminal 5 arrivals.'
  });

  const museum = visit({
    id: 'seg-soane',
    tripId,
    name: "Sir John Soane's Museum",
    category: 'Museum',
    place: soane,
    date: landed.add({ days: 1 }),
    from: localTime(10, 30),
    to: localTime(12, 30),
    zone: LONDON,
    travelerIds: party,
    curatedBy: 'Free entry, timed ticket. Go early; the rooms are small.'
  });

  const market = visit({
    id: 'seg-borough',
    tripId,
    name: 'Lunch at Borough Market',
    category: 'Food',
    place: borough,
    date: landed.add({ days: 1 }),
    from: localTime(13, 15),
    to: localTime(14, 45),
    zone: LONDON,
    travelerIds: party,
    curatedBy: 'Closed Sundays. Cash is not needed anywhere.'
  });

  // The split: Julian goes ahead on the morning train, Mara follows that evening.
  const splitDate = landed.add({ days: 4 });
  const julianTrain = ground({
    id: 'seg-eurostar-julian',
    tripId,
    mode: 'TRAIN',
    origin: stPancras,
    destination: gareDuNord,
    startEpochMillis: at(splitDate, 8, 31, LONDON),
    endEpochMillis: at(splitDate, 11, 47, PARIS),
    startZoneId: LONDON,
    endZoneId: PARIS,
    travelerIds: [julian.id],
    confirmationCode: 'XQ4M2T',
    bookedWith: 'Eurostar',
    provider: 'Eurostar 9014',
    pickupInstruction: 'Check-in closes 30 minutes before departure. Passport control on departure.'
  });

  const maraTrain = ground({
    id: 'seg-eurostar-mara',
    tripId,
    mode: 'TRAIN',
    origin: stPancras,
    destination: gareDuNord,
    startEpochMillis: at(splitDate, 19, 4, LONDON),
    endEpochMillis: at(splitDate, 22, 20, PARIS),
    startZoneId: LONDON,
    endZoneId: PARIS,
    travelerIds: [mara.id],
    confirmationCode: 'XQ4M9B',
    bookedWith: 'Eurostar',
    provider: 'Eurostar 9054',
    pickupInstruction: 'Same check-in rules. The later train is quieter.'
  });

  const hotelParis = lodging({
    id: 'seg-hotel-paris',
    tripId,
    propertyName: saintJacques.name,
    origin: saintJacques,
    startEpochMillis: at(splitDate, 14, 0, PARIS),
    endEpochMillis: at(splitDate.add({ days: 3 }), 11, 0, PARIS),
    startZoneId: PARIS,
    endZoneId: PARIS,
    travelerIds: party,
    confirmationCode: 'HSJ-90211',
    bookedWith: 'Booking agent',
    roomDescription: 'One room with two beds, courtyard side'
  });

  const orangerieVisit = visit({
    id: 'seg-orangerie',
    tripId,
    name: "Musée de l'Orangerie",
    category: 'Museum',
    place: orangerie,
    date: splitDate.add({ days: 1 }),
    from: localTime(9, 30),
    to: localTime(11, 30),
    zone: PARIS,
    travelerIds: party,
    confirmationCode: 'ORG-2210-4419',
    bookedWith: 'Musées nationaux',
    curatedBy: 'Timed entry. The Nymphéas rooms are quietest at opening.'
  });

  const dinner = visit({
    id: 'seg-baratin',
    tripId,
    name: 'Dinner at Le Baratin',
    category: 'Restaurant',
    place: baratin,
    date: splitDate.add({ days: 1 }),
    from: localTime(20, 0),
    to: localTime(22, 30),
    zone: PARIS,
    travelerIds: party,
    bookedWith: 'Telephone booking',
    curatedBy: 'Belleville, up the hill. Book by telephone; they do not take email.'
  });

  const returnDate = splitDate.add({ days: 3 });
  const homeward = flight({
    id: 'seg-return',
    tripId,
    carrierCode: 'AF',
    flightNumber: '84',
    origin: airportPlaceFor('CDG'),
    destination: airportPlaceFor('SFO'),
    startEpochMillis: at(returnDate, 13, 25, PARIS),
    endEpochMillis: at(returnDate, 15, 40, PACIFIC),
    startZoneId: PARIS,
    endZoneId: PACIFIC,
    travelerIds: party,
    confirmationCode: 'R3PT8L',
    bookedWith: 'Air France',
    ticketNumbers: { [mara.id]: '057-2118330421', [julian.id]: '057-2118330422' },
    departureTerminal: '2E',
    arrivalTerminal: 'I',
    aircraft: 'Boeing 777-300ER',
    cabin: 'Economy',
    seats: { [mara.id]: '34K', [julian.id]: '34J' }
  });

  const toAirport = ground({
    id: 'seg-cdg-transfer',
    tripId,
    mode: 'TRAIN',
    origin: saintJacques,
    destination: airportPlaceFor('CDG'),
    startEpochMillis: homeward.startEpochMillis - 195 * MINUTE,
    endEpochMillis: homeward.startEpochMillis - 140 * MINUTE,
    startZoneId: PARIS,
    endZoneId: PARIS,
    travelerIds: party,
    provider: 'RER B from Luxembourg',
    pickupInstruction: 'Buy the airport fare at the machine; city tickets are not valid.'
  });

  const segments: Segment[] = [
    outbound,
    airportTrain,
    hotelLondon,
    museum,
    market,
    julianTrain,
    maraTrain,
    hotelParis,
    orangerieVisit,
    dinner,
    toAirport,
    homeward
  ];

  const trip = makeTrip({
    id: tripId,
    name: SAMPLE_TRIP_NAME,
    destinationSummary: 'London, then Paris',
    startEpochMillis: Math.min(...segments.map((s) => s.startEpochMillis)),
    endEpochMillis: Math.max(...segments.map((s) => s.endEpochMillis)),
    homeZoneId: PACIFIC
  });

  const ideas: Idea[] = [
    listIdea({
      id: 'idea-wallace',
      tripId,
      city: 'London',
      title: 'The Wallace Collection',
      kind: 'SEE',
      minutes: 90,
      note: 'A house full of paintings, free, and never busy.',
      latitude: 51.5175,
      longitude: -0.1527
    }),
    {
      ...listIdea({
        id: 'idea-canal',
        tripId,
        city: 'London',
        title: "Regent's Canal: Angel to Broadway Market",
        kind: 'DO',
        minutes: 75,
        note: 'Flat the whole way. Locks, houseboats, and a market at the end.',
        latitude: 51.533,
        longitude: -0.103
      }),
      interestedTravelerIds: [julian.id]
    },
    listIdea({
      id: 'idea-beigel',
      tripId,
      city: 'London',
      title: 'Salt beef beigel, Brick Lane',
      kind: 'EAT',
      minutes: 20,
      note: 'Open at every hour there is.',
      latitude: 51.524,
      longitude: -0.0716
    }),
    {
      ...listIdea({
        id: 'idea-roast',
        tripId,
        city: 'London',
        title: 'A proper Sunday roast',
        kind: 'EAT',
        minutes: 120,
        note: 'Book it. The good ones are gone by Friday.'
      }),
      status: 'DONE'
    },
    {
      ...listIdea({
        id: 'idea-rodin',
        tripId,
        city: 'Paris',
        title: 'Musée Rodin',
        kind: 'SEE',
        minutes: 90,
        note: 'Go for the garden as much as the sculpture.',
        latitude: 48.8553,
        longitude: 2.3158,
        zone: PARIS
      }),
      interestedTravelerIds: [mara.id]
    },
    listIdea({
      id: 'idea-aligre',
      tripId,
      city: 'Paris',
      title: "Marché d'Aligre",
      kind: 'SHOP',
      minutes: 60,
      note: 'Mornings only, and closed on Mondays.',
      latitude: 48.85,
      longitude: 2.3782,
      zone: PARIS
    }),
    listIdea({
      id: 'idea-nata',
      tripId,
      city: 'Paris',
      title: 'Jambon-beurre',
      kind: 'EAT',
      minutes: 15,
      note: 'A baguette, ham and butter. The whole point is the bread.'
    }),
    makeIdea({
      id: 'idea-bookshop',
      tripId,
      title: 'The secondhand bookshop Julian mentioned',
      kind: 'SHOP',
      city: 'London',
      note: 'Somewhere off Charing Cross Road. Ask him which one.',
      interestedTravelerIds: [julian.id]
    })
  ];

  return { trip, travelers: [mara, julian], segments, ideas };
}

/** One line on the ideas board; a place only where the thing is somewhere. */
function listIdea(args: {
  id: string;
  tripId: string;
  city: string;
  title: string;
  kind: IdeaKind;
  minutes: number;
  note: string;
  latitude?: number;
  longitude?: number;
  zone?: string;
}): Idea {
  const { latitude = 0, longitude = 0, zone = LONDON } = args;
  return makeIdea({
    id: args.id,
    tripId: args.tripId,
    title: args.title,
    kind: args.kind,
    city: args.city,
    note: args.note,
    typicalMinutes: args.minutes,
    place:
      latitude === 0 && longitude === 0
        ? null
        : place({ name: args.title, city: args.city, latitude, longitude, timeZoneId: zone })
  });
}

function visit(args: {
  id: string;
  tripId: string;
  name: string;
  category: string;
  place: Place;
  date: LocalDate;
  from: LocalTime;
  to: LocalTime;
  zone: string;
  travelerIds: string[];
  confirmationCode?: string;
  bookedWith?: string;
  curatedBy?: string;
}): Experience {
  return experienceSegment({
    id: args.id,
    tripId: args.tripId,
    name: args.name,
    category: args.category,
    origin: args.place,
    startEpochMillis: zonedOf(args.date, args.from, args.zone).epochMilliseconds,
    endEpochMillis: zonedOf(args.date, args.to, args.zone).epochMilliseconds,
    startZoneId: args.zone,
    endZoneId: args.zone,
    travelerIds: args.travelerIds,
    confirmationCode: args.confirmationCode ?? null,
    bookedWith: args.bookedWith ?? null,
    curatedBy: args.curatedBy ?? null
  });
}
