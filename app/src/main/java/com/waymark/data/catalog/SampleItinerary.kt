package com.waymark.data.catalog

import com.waymark.domain.logic.Bcbp
import com.waymark.domain.model.BoardingPass
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.PackingItem
import com.waymark.domain.model.TravelDocument
import com.waymark.domain.model.Place
import com.waymark.domain.model.Reservation
import com.waymark.domain.model.SeatPreference
import com.waymark.domain.model.Secret
import com.waymark.domain.model.SecretField
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.Trip
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A worked example, written to the database on first run so the app opens on
 * something rather than an empty state — and so every feature has a real case
 * to render: a shared outbound, an itinerary that splits for a day, a train
 * across a border, and a vault with codes in it.
 *
 * Fictional travelers, real stations, plausible times.
 */
object SampleItinerary {

    data class Bundle(
        val trip: Trip,
        val travelers: List<Traveler>,
        val segments: List<Segment>,
        val reservations: List<Reservation>,
        val passes: List<BoardingPass>,
        val ideas: List<Idea>,
        val documents: List<TravelDocument>,
        val packing: List<PackingItem>,
    )

    private val london = ZoneId.of("Europe/London")
    private val paris = ZoneId.of("Europe/Paris")
    private val pacific = ZoneId.of("America/Los_Angeles")

    private val bloomsbury = Place(
        name = "The Bloomsbury Rooms", city = "London", country = "United Kingdom",
        latitude = 51.5205, longitude = -0.1265, timeZoneId = "Europe/London",
        address = "Montague Street, Bloomsbury, London WC1B",
    )
    private val stPancras = Place(
        name = "St Pancras International", code = "QQS", city = "London",
        country = "United Kingdom", latitude = 51.5308, longitude = -0.1261,
        timeZoneId = "Europe/London", address = "Euston Road, London N1C",
    )
    private val gareDuNord = Place(
        name = "Gare du Nord", code = "XPG", city = "Paris", country = "France",
        latitude = 48.8809, longitude = 2.3553, timeZoneId = "Europe/Paris",
        address = "18 Rue de Dunkerque, 75010 Paris",
    )
    private val saintJacques = Place(
        name = "Hôtel Saint-Jacques", city = "Paris", country = "France",
        latitude = 48.8462, longitude = 2.3466, timeZoneId = "Europe/Paris",
        address = "35 Rue des Écoles, 75005 Paris",
    )
    private val soane = Place(
        name = "Sir John Soane's Museum", city = "London", country = "United Kingdom",
        latitude = 51.5170, longitude = -0.1170, timeZoneId = "Europe/London",
        address = "13 Lincoln's Inn Fields, London WC2A",
    )
    private val borough = Place(
        name = "Borough Market", city = "London", country = "United Kingdom",
        latitude = 51.5055, longitude = -0.0910, timeZoneId = "Europe/London",
    )
    private val orangerie = Place(
        name = "Musée de l'Orangerie", city = "Paris", country = "France",
        latitude = 48.8638, longitude = 2.3226, timeZoneId = "Europe/Paris",
        address = "Jardin des Tuileries, 75001 Paris",
    )
    private val baratin = Place(
        name = "Le Baratin", city = "Paris", country = "France",
        latitude = 48.8722, longitude = 2.3877, timeZoneId = "Europe/Paris",
        address = "3 Rue Jouye-Rouve, 75020 Paris",
    )

    /** @param departure the local date the party leaves San Francisco. */
    fun build(departure: LocalDate): Bundle {
        val tripId = "trip-sample"
        val mara = Traveler(
            id = "trav-mara",
            fullName = "Mara Ellison",
            nickname = "Mara",
            seatPreference = SeatPreference.WINDOW,
            mealPreference = "Vegetarian",
            contactPhone = "+1 415 555 0148",
        )
        val julian = Traveler(
            id = "trav-julian",
            fullName = "Julian Beck",
            nickname = "Julian",
            seatPreference = SeatPreference.AISLE,
            contactPhone = "+1 415 555 0172",
        )
        val party = setOf(mara.id, julian.id)

        val outbound = requireNotNull(FlightCatalog.lookup("BA286", departure)) {
            "The bundled catalog must contain BA286"
        }.toSegment(
            id = "seg-outbound",
            tripId = tripId,
            travelerIds = party,
            reservationId = "res-outbound",
            seats = mapOf(mara.id to "21A", julian.id to "21C"),
            cabin = "Economy",
        )

        val londonCheckIn = ZonedDateTime.of(
            outbound.end.toLocalDate(), LocalTime.of(15, 0), london,
        )
        val londonCheckOut = ZonedDateTime.of(
            outbound.end.toLocalDate().plusDays(4), LocalTime.of(11, 0), london,
        )
        val hotelLondon = Segment.Lodging(
            id = "seg-hotel-london",
            tripId = tripId,
            propertyName = bloomsbury.name,
            origin = bloomsbury,
            startEpochMillis = londonCheckIn.toInstant().toEpochMilli(),
            endEpochMillis = londonCheckOut.toInstant().toEpochMilli(),
            startZoneId = london.id,
            endZoneId = london.id,
            travelerIds = party,
            reservationId = "res-hotel-london",
            roomDescription = "Two rooms, second floor, garden side",
            checkInNote = "Reception is staffed from 07:00. Late arrivals collect a key from the porter.",
        )

        val airportTrain = Segment.Ground(
            id = "seg-heathrow-express",
            tripId = tripId,
            mode = GroundMode.TRAIN,
            origin = Airports.place("LHR"),
            destination = stPancras,
            startEpochMillis = outbound.endEpochMillis + 55 * 60_000L,
            endEpochMillis = outbound.endEpochMillis + 110 * 60_000L,
            startZoneId = london.id,
            endZoneId = london.id,
            travelerIds = party,
            provider = "Elizabeth line",
            pickupInstruction = "Platform level, follow signs from Terminal 5 arrivals.",
        )

        val museum = experience(
            id = "seg-soane",
            tripId = tripId,
            name = "Sir John Soane's Museum",
            category = "Museum",
            place = soane,
            date = outbound.end.toLocalDate().plusDays(1),
            from = LocalTime.of(10, 30),
            to = LocalTime.of(12, 30),
            zone = london,
            travelerIds = party,
            curatedBy = "Free entry, timed ticket. Go early; the rooms are small.",
        )

        val market = experience(
            id = "seg-borough",
            tripId = tripId,
            name = "Lunch at Borough Market",
            category = "Food",
            place = borough,
            date = outbound.end.toLocalDate().plusDays(1),
            from = LocalTime.of(13, 15),
            to = LocalTime.of(14, 45),
            zone = london,
            travelerIds = party,
            curatedBy = "Closed Sundays. Cash is not needed anywhere.",
        )

        // The split: Julian goes ahead on the morning train, Mara follows that evening.
        val splitDate = outbound.end.toLocalDate().plusDays(4)
        val julianTrain = Segment.Ground(
            id = "seg-eurostar-julian",
            tripId = tripId,
            mode = GroundMode.TRAIN,
            origin = stPancras,
            destination = gareDuNord,
            startEpochMillis = ZonedDateTime.of(splitDate, LocalTime.of(8, 31), london)
                .toInstant().toEpochMilli(),
            endEpochMillis = ZonedDateTime.of(splitDate, LocalTime.of(11, 47), paris)
                .toInstant().toEpochMilli(),
            startZoneId = london.id,
            endZoneId = paris.id,
            travelerIds = setOf(julian.id),
            reservationId = "res-train-julian",
            provider = "Eurostar 9014",
            pickupInstruction = "Check-in closes 30 minutes before departure. Passport control on departure.",
        )

        val maraTrain = Segment.Ground(
            id = "seg-eurostar-mara",
            tripId = tripId,
            mode = GroundMode.TRAIN,
            origin = stPancras,
            destination = gareDuNord,
            startEpochMillis = ZonedDateTime.of(splitDate, LocalTime.of(19, 4), london)
                .toInstant().toEpochMilli(),
            endEpochMillis = ZonedDateTime.of(splitDate, LocalTime.of(22, 20), paris)
                .toInstant().toEpochMilli(),
            startZoneId = london.id,
            endZoneId = paris.id,
            travelerIds = setOf(mara.id),
            reservationId = "res-train-mara",
            provider = "Eurostar 9054",
            pickupInstruction = "Same check-in rules. The later train is quieter.",
        )

        val hotelParis = Segment.Lodging(
            id = "seg-hotel-paris",
            tripId = tripId,
            propertyName = saintJacques.name,
            origin = saintJacques,
            startEpochMillis = ZonedDateTime.of(splitDate, LocalTime.of(14, 0), paris)
                .toInstant().toEpochMilli(),
            endEpochMillis = ZonedDateTime.of(splitDate.plusDays(3), LocalTime.of(11, 0), paris)
                .toInstant().toEpochMilli(),
            startZoneId = paris.id,
            endZoneId = paris.id,
            travelerIds = party,
            reservationId = "res-hotel-paris",
            roomDescription = "One room with two beds, courtyard side",
        )

        val orangerieVisit = experience(
            id = "seg-orangerie",
            tripId = tripId,
            name = "Musée de l'Orangerie",
            category = "Museum",
            place = orangerie,
            date = splitDate.plusDays(1),
            from = LocalTime.of(9, 30),
            to = LocalTime.of(11, 30),
            zone = paris,
            travelerIds = party,
            reservationId = "res-orangerie",
            curatedBy = "Timed entry. The Nymphéas rooms are quietest at opening.",
        )

        val dinner = experience(
            id = "seg-baratin",
            tripId = tripId,
            name = "Dinner at Le Baratin",
            category = "Restaurant",
            place = baratin,
            date = splitDate.plusDays(1),
            from = LocalTime.of(20, 0),
            to = LocalTime.of(22, 30),
            zone = paris,
            travelerIds = party,
            reservationId = "res-baratin",
            curatedBy = "Belleville, up the hill. Book by telephone; they do not take email.",
        )

        val returnDate = splitDate.plusDays(3)
        val homeward = Segment.Flight(
            id = "seg-return",
            tripId = tripId,
            carrierCode = "AF",
            flightNumber = "84",
            origin = Airports.place("CDG"),
            destination = Airports.place("SFO"),
            startEpochMillis = ZonedDateTime.of(returnDate, LocalTime.of(13, 25), paris)
                .toInstant().toEpochMilli(),
            endEpochMillis = ZonedDateTime.of(returnDate, LocalTime.of(15, 40), pacific)
                .toInstant().toEpochMilli(),
            startZoneId = paris.id,
            endZoneId = pacific.id,
            travelerIds = party,
            reservationId = "res-return",
            departureTerminal = "2E",
            arrivalTerminal = "I",
            aircraft = "Boeing 777-300ER",
            cabin = "Economy",
            seats = mapOf(mara.id to "34K", julian.id to "34J"),
        )

        val toAirport = Segment.Ground(
            id = "seg-cdg-transfer",
            tripId = tripId,
            mode = GroundMode.TRAIN,
            origin = saintJacques,
            destination = Airports.place("CDG"),
            startEpochMillis = homeward.startEpochMillis - 195 * 60_000L,
            endEpochMillis = homeward.startEpochMillis - 140 * 60_000L,
            startZoneId = paris.id,
            endZoneId = paris.id,
            travelerIds = party,
            provider = "RER B from Luxembourg",
            pickupInstruction = "Buy the airport fare at the machine; city tickets are not valid.",
        )

        val segments = listOf(
            outbound, airportTrain, hotelLondon, museum, market,
            julianTrain, maraTrain, hotelParis, orangerieVisit, dinner,
            toAirport, homeward,
        )

        val trip = Trip(
            id = tripId,
            name = "London & Paris",
            destinationSummary = "London, then Paris",
            startEpochMillis = segments.minOf { it.startEpochMillis },
            endEpochMillis = segments.maxOf { it.endEpochMillis },
            homeZoneId = pacific.id,
            coverPlace = bloomsbury,
        )

        val reservations = listOf(
            Reservation(
                id = "res-outbound", tripId = tripId, segmentId = outbound.id,
                label = "BA286 · SFO → LHR", vendor = "British Airways",
                kind = SegmentKind.FLIGHT, travelerIds = party,
                secrets = listOf(
                    Secret(SecretField.RECORD_LOCATOR, "K7QH2P"),
                    Secret(SecretField.ETICKET_NUMBER, "125-2364119807", mara.id),
                    Secret(SecretField.ETICKET_NUMBER, "125-2364119808", julian.id),
                ),
            ),
            Reservation(
                id = "res-hotel-london", tripId = tripId, segmentId = hotelLondon.id,
                label = "The Bloomsbury Rooms", vendor = "Direct booking",
                kind = SegmentKind.LODGING, travelerIds = party,
                secrets = listOf(Secret(SecretField.CONFIRMATION_CODE, "BLM-4471-QE")),
            ),
            Reservation(
                id = "res-train-julian", tripId = tripId, segmentId = julianTrain.id,
                label = "Eurostar 9014 · London → Paris", vendor = "Eurostar",
                kind = SegmentKind.GROUND, travelerIds = setOf(julian.id),
                secrets = listOf(Secret(SecretField.CONFIRMATION_CODE, "XQ4M2T", julian.id)),
            ),
            Reservation(
                id = "res-train-mara", tripId = tripId, segmentId = maraTrain.id,
                label = "Eurostar 9054 · London → Paris", vendor = "Eurostar",
                kind = SegmentKind.GROUND, travelerIds = setOf(mara.id),
                secrets = listOf(Secret(SecretField.CONFIRMATION_CODE, "XQ4M9B", mara.id)),
            ),
            Reservation(
                id = "res-hotel-paris", tripId = tripId, segmentId = hotelParis.id,
                label = "Hôtel Saint-Jacques", vendor = "Booking agent",
                kind = SegmentKind.LODGING, travelerIds = party,
                secrets = listOf(Secret(SecretField.CONFIRMATION_CODE, "HSJ-90211")),
            ),
            Reservation(
                id = "res-orangerie", tripId = tripId, segmentId = orangerieVisit.id,
                label = "Orangerie · timed entry", vendor = "Musées nationaux",
                kind = SegmentKind.EXPERIENCE, travelerIds = party,
                secrets = listOf(Secret(SecretField.GATE_PASS, "ORG-2210-4419")),
            ),
            Reservation(
                id = "res-baratin", tripId = tripId, segmentId = dinner.id,
                label = "Le Baratin · table for two", vendor = "Telephone booking",
                kind = SegmentKind.EXPERIENCE, travelerIds = party,
                secrets = listOf(Secret(SecretField.OTHER, "Booked under Ellison, 20:00")),
            ),
            Reservation(
                id = "res-return", tripId = tripId, segmentId = homeward.id,
                label = "AF84 · CDG → SFO", vendor = "Air France",
                kind = SegmentKind.FLIGHT, travelerIds = party,
                secrets = listOf(
                    Secret(SecretField.RECORD_LOCATOR, "R3PT8L"),
                    Secret(SecretField.ETICKET_NUMBER, "057-2118330421", mara.id),
                    Secret(SecretField.ETICKET_NUMBER, "057-2118330422", julian.id),
                ),
            ),
        )

        val passes = listOf(
            boardingPass(outbound, mara, "21A", "042", "3", "K7QH2P", tripId),
            boardingPass(outbound, julian, "21C", "043", "3", "K7QH2P", tripId),
        )

        // A few things on the list with no date on them yet — the half of a
        // trip that reservations cannot hold.
        val ideas = listOf(
            guideIdea("idea-wallace", tripId, "London", "The Wallace Collection"),
            guideIdea("idea-canal", tripId, "London", "Regent's Canal: Angel to Broadway Market")
                .copy(interestedTravelerIds = setOf(julian.id)),
            guideIdea("idea-beigel", tripId, "London", "Salt beef beigel, Brick Lane"),
            guideIdea("idea-roast", tripId, "London", "A proper Sunday roast")
                .copy(status = IdeaStatus.DONE),
            guideIdea("idea-rodin", tripId, "Paris", "Musée Rodin")
                .copy(interestedTravelerIds = setOf(mara.id)),
            guideIdea("idea-aligre", tripId, "Paris", "Marché d'Aligre"),
            guideIdea("idea-nata", tripId, "Paris", "Jambon-beurre"),
            Idea(
                id = "idea-bookshop",
                tripId = tripId,
                title = "The secondhand bookshop Julian mentioned",
                kind = IdeaKind.SHOP,
                city = "London",
                note = "Somewhere off Charing Cross Road. Ask him which one.",
                interestedTravelerIds = setOf(julian.id),
            ),
        )

        // One passport comfortably valid, one inside the six-month margin —
        // the case the expiry rule exists to catch.
        val returnDay = homeward.end.toLocalDate()
        val documents = listOf(
            TravelDocument(
                id = "doc-mara-passport",
                travelerId = mara.id,
                kind = DocumentKind.PASSPORT,
                label = "Passport",
                number = "509384711",
                issuer = "United States",
                issuedOn = returnDay.minusYears(6),
                expiresOn = returnDay.plusYears(4),
            ),
            TravelDocument(
                id = "doc-julian-passport",
                travelerId = julian.id,
                kind = DocumentKind.PASSPORT,
                label = "Passport",
                number = "488120953",
                issuer = "United States",
                issuedOn = returnDay.minusYears(9),
                expiresOn = returnDay.plusMonths(4),
                note = "Renewal takes six to eight weeks at the moment.",
            ),
            TravelDocument(
                id = "doc-shared-insurance",
                travelerId = mara.id,
                kind = DocumentKind.INSURANCE,
                label = "Travel insurance, both travelers",
                number = "TI-88240-EU",
                issuer = "Meridian Cover",
                expiresOn = returnDay.plusMonths(7),
                note = "Medical to \u00a35m, cancellation to \u00a32,500 each.",
            ),
        )

        val packing = listOf(
            PackingItem(
                id = "pack-shared-adaptor",
                tripId = tripId,
                travelerId = null,
                title = "Travel adaptor (Type G)",
                category = PackingCategory.ELECTRONICS,
                quantity = 2,
                essential = true,
                note = "One for the room, one for the bag.",
            ),
            PackingItem(
                id = "pack-shared-kit",
                tripId = tripId,
                travelerId = null,
                title = "First-aid kit",
                category = PackingCategory.HEALTH,
            ),
            PackingItem(
                id = "pack-mara-passport",
                tripId = tripId,
                travelerId = mara.id,
                title = "Passport",
                category = PackingCategory.DOCUMENTS,
                essential = true,
                packed = true,
            ),
            PackingItem(
                id = "pack-julian-shoes",
                tripId = tripId,
                travelerId = julian.id,
                title = "Shoes you can walk all day in",
                category = PackingCategory.CLOTHING,
                essential = true,
            ),
        )

        return Bundle(
            trip = trip,
            travelers = listOf(mara, julian),
            segments = segments,
            reservations = reservations,
            passes = passes,
            ideas = ideas,
            documents = documents,
            packing = packing,
        )
    }

    private fun FlightCatalog.FlightPlan.toSegment(
        id: String,
        tripId: String,
        travelerIds: Set<String>,
        reservationId: String?,
        seats: Map<String, String>,
        cabin: String,
    ): Segment.Flight = Segment.Flight(
        id = id,
        tripId = tripId,
        carrierCode = designator.takeWhile { !it.isDigit() },
        flightNumber = designator.dropWhile { !it.isDigit() },
        origin = origin,
        destination = destination,
        startEpochMillis = departure.toInstant().toEpochMilli(),
        endEpochMillis = arrival.toInstant().toEpochMilli(),
        startZoneId = origin.timeZoneId,
        endZoneId = destination.timeZoneId,
        travelerIds = travelerIds,
        reservationId = reservationId,
        departureTerminal = departureTerminal,
        arrivalTerminal = arrivalTerminal,
        aircraft = aircraft,
        cabin = cabin,
        seats = seats,
        operatedBy = carrierName,
    )

    /** Pull one entry out of the bundled guide, by city and title. */
    private fun guideIdea(id: String, tripId: String, city: String, title: String): Idea {
        val entry = requireNotNull(
            DestinationGuide.forCity(city).firstOrNull { it.title == title }
        ) { "The bundled guide must contain \"$title\" for $city" }
        return with(DestinationGuide) { entry.toIdea(id = id, tripId = tripId, city = city) }
    }

    private fun experience(
        id: String,
        tripId: String,
        name: String,
        category: String,
        place: Place,
        date: LocalDate,
        from: LocalTime,
        to: LocalTime,
        zone: ZoneId,
        travelerIds: Set<String>,
        reservationId: String? = null,
        curatedBy: String? = null,
    ): Segment.Experience = Segment.Experience(
        id = id,
        tripId = tripId,
        name = name,
        category = category,
        origin = place,
        startEpochMillis = ZonedDateTime.of(date, from, zone).toInstant().toEpochMilli(),
        endEpochMillis = ZonedDateTime.of(date, to, zone).toInstant().toEpochMilli(),
        startZoneId = zone.id,
        endZoneId = zone.id,
        travelerIds = travelerIds,
        reservationId = reservationId,
        curatedBy = curatedBy,
    )

    private fun boardingPass(
        flight: Segment.Flight,
        traveler: Traveler,
        seat: String,
        sequence: String,
        group: String,
        recordLocator: String,
        tripId: String,
    ): BoardingPass {
        val date = flight.start.toLocalDate()
        return BoardingPass(
            id = "pass-${flight.id}-${traveler.id}",
            tripId = tripId,
            segmentId = flight.id,
            travelerId = traveler.id,
            passengerName = traveler.fullName,
            designator = flight.designator,
            origin = flight.origin.code.orEmpty(),
            destination = flight.destination.code.orEmpty(),
            seat = seat,
            boardingGroup = group,
            sequenceNumber = sequence,
            gate = flight.departureGate,
            boardingTimeMillis = Bcbp.defaultBoardingTime(flight.startEpochMillis),
            cabin = flight.cabin,
            fastTrack = false,
            barcodePayload = Bcbp.build(
                passengerName = traveler.fullName,
                recordLocator = recordLocator,
                origin = flight.origin.code.orEmpty(),
                destination = flight.destination.code.orEmpty(),
                carrier = flight.carrierCode,
                flightNumber = flight.flightNumber,
                date = date,
                seat = seat,
                sequence = sequence,
            ),
        )
    }
}
