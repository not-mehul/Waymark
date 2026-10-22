package com.waymark.data.share

import com.waymark.data.catalog.Airports
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.PriceBand
import com.waymark.domain.model.SeatPreference
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.Trip
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Trips as a file: a backup of everything, or one trip sent to the rest of the
 * party.
 *
 * The format is its own, not the database's rows, so the database can change
 * shape without making old files unreadable — [WaymarkArchive.version] is what
 * a future reader branches on. It is JSON because a person should be able to
 * open the file and see their itinerary in it.
 *
 * Reading is the same for both uses: every trip in the file is taken as the
 * current version of that trip. A trip already on the phone is replaced by the
 * file's copy, and one that is not is added — so sending an updated itinerary
 * to someone who already has the old one updates it rather than doubling it.
 */
object TripArchive {

    const val FORMAT = "waymark"
    const val VERSION = 1
    const val MIME = "application/json"

    /**
     * The most a QR code can carry in byte mode at the lowest error
     * correction, less the `waymark://import?d=` prefix.
     */
    const val MAX_CODE_CHARS = 2_900

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = false
    }

    private val pretty = Json(json) { prettyPrint = true }

    class InvalidArchive(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** One trip and everything on it, as the domain knows it. */
    data class TripBundle(
        val trip: Trip,
        val travelers: List<Traveler>,
        val segments: List<Segment>,
        val ideas: List<Idea>,
    )

    fun archiveOf(bundles: List<TripBundle>, nowMillis: Long = System.currentTimeMillis()): WaymarkArchive =
        WaymarkArchive(
            format = FORMAT,
            version = VERSION,
            exportedAt = nowMillis,
            travelers = bundles.flatMap { it.travelers }.distinctBy { it.id }.map(::toArchive),
            trips = bundles.map { bundle ->
                ArchiveTrip(
                    id = bundle.trip.id,
                    name = bundle.trip.name,
                    destinationSummary = bundle.trip.destinationSummary,
                    start = bundle.trip.startEpochMillis,
                    end = bundle.trip.endEpochMillis,
                    homeZone = bundle.trip.homeZoneId,
                    archived = bundle.trip.archived,
                    party = bundle.travelers.map { it.id },
                    segments = bundle.segments.map(::toArchive),
                    ideas = bundle.ideas.map(::toArchive),
                )
            },
        )

    fun encode(archive: WaymarkArchive): String = pretty.encodeToString(WaymarkArchive.serializer(), archive)

    fun decode(text: String): WaymarkArchive {
        val archive = try {
            json.decodeFromString(WaymarkArchive.serializer(), text)
        } catch (error: Exception) {
            throw InvalidArchive("This is not a Waymark file.", error)
        }
        if (archive.format != FORMAT) throw InvalidArchive("This is not a Waymark file.")
        if (archive.version > VERSION) {
            throw InvalidArchive("This file is from a newer Waymark. Update the app to open it.")
        }
        return restore(archive)
    }

    /** The archive's trips as the domain knows them. */
    fun bundlesOf(archive: WaymarkArchive): List<TripBundle> {
        val travelers = archive.travelers.associate { it.id to fromArchive(it) }
        return archive.trips.map { trip ->
            TripBundle(
                trip = Trip(
                    id = trip.id,
                    name = trip.name,
                    destinationSummary = trip.destinationSummary,
                    startEpochMillis = trip.start,
                    endEpochMillis = trip.end,
                    homeZoneId = trip.homeZone,
                    archived = trip.archived,
                ),
                travelers = trip.party.mapNotNull { travelers[it] },
                segments = trip.segments.mapNotNull { fromArchive(it, trip.id) },
                ideas = trip.ideas.mapNotNull { fromArchive(it, trip.id) },
            )
        }
    }

    /** How much of a trip a code carries: as much as fits, in this order. */
    enum class CodeDetail(val omits: String?) {
        EVERYTHING(null),
        BOOKINGS_AND_NOTES("the ideas list"),
        BOOKINGS("the ideas list and long notes"),
    }

    data class Code(val payload: String, val detail: CodeDetail)

    /**
     * The archive as a QR code's payload, carrying as much as fits: everything
     * if it can, then without the ideas, then without the long notes too. Null
     * when even the bookings alone are more than a code can carry — the file
     * is the way to send that trip.
     */
    fun toCode(archive: WaymarkArchive): Code? = CodeDetail.entries.firstNotNullOfOrNull { detail ->
        encodeCode(lean(trimmed(archive, detail)))?.let { Code(it, detail) }
    }

    private fun trimmed(archive: WaymarkArchive, detail: CodeDetail): WaymarkArchive = when (detail) {
        CodeDetail.EVERYTHING -> archive
        CodeDetail.BOOKINGS_AND_NOTES -> archive.copy(trips = archive.trips.map { it.copy(ideas = emptyList()) })
        CodeDetail.BOOKINGS -> archive.copy(
            trips = archive.trips.map { trip ->
                trip.copy(
                    ideas = emptyList(),
                    segments = trip.segments.map {
                        it.copy(note = null, checkInNote = null, pickup = null, tips = null)
                    },
                )
            },
        )
    }

    /**
     * Everything a code can leave out because the receiving phone can work it
     * out: a station is its code, a booking's zones are its places' zones, and
     * a stay's destination is its origin.
     */
    private fun lean(archive: WaymarkArchive): WaymarkArchive = archive.copy(
        trips = archive.trips.map { trip ->
            trip.copy(
                segments = trip.segments.map { row ->
                    val destination = row.destination?.takeUnless { it == row.origin }
                    row.copy(
                        origin = leanPlace(row.origin),
                        destination = destination?.let(::leanPlace),
                        startZone = row.startZone.takeUnless { it == row.origin.zone }.orEmpty(),
                        endZone = row.endZone.takeUnless { it == (destination ?: row.origin).zone }.orEmpty(),
                    )
                },
            )
        },
    )

    private fun leanPlace(place: ArchivePlace): ArchivePlace {
        val code = place.code ?: return place
        val catalogue = Airports.find(code)?.toPlace()?.let(::toArchive) ?: return place
        return if (catalogue == place) ArchivePlace(code = code) else place
    }

    /** The inverse of [lean], run on every archive read so a lean one reads whole. */
    private fun restore(archive: WaymarkArchive): WaymarkArchive = archive.copy(
        trips = archive.trips.map { trip ->
            trip.copy(
                segments = trip.segments.map { row ->
                    val origin = restorePlace(row.origin)
                    val destination = row.destination?.let(::restorePlace) ?: origin
                    row.copy(
                        origin = origin,
                        destination = destination,
                        startZone = row.startZone.ifEmpty { origin.zone },
                        endZone = row.endZone.ifEmpty { destination.zone },
                    )
                },
            )
        },
    )

    private fun restorePlace(place: ArchivePlace): ArchivePlace {
        val code = place.code
        if (code == null || place.name.isNotEmpty()) return place
        return Airports.find(code)?.toPlace()?.let(::toArchive) ?: place.copy(name = code)
    }

    private fun encodeCode(archive: WaymarkArchive): String? {
        val bytes = json.encodeToString(WaymarkArchive.serializer(), archive).toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return code.takeIf { it.length <= MAX_CODE_CHARS }
    }

    fun fromCode(code: String): WaymarkArchive {
        val bytes = try {
            Base64.getUrlDecoder().decode(code.trim())
        } catch (error: IllegalArgumentException) {
            throw InvalidArchive("That code is not a Waymark trip.", error)
        }
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, read)
                // A trip is kilobytes; a "trip" that inflates past a megabyte is not one.
                if (out.size() > MAX_INFLATED_BYTES) throw InvalidArchive("That code is too large to be a trip.")
            }
        } catch (error: java.util.zip.DataFormatException) {
            throw InvalidArchive("That code is not a Waymark trip.", error)
        } finally {
            inflater.end()
        }
        return decode(out.toString(Charsets.UTF_8.name()))
    }

    private const val MAX_INFLATED_BYTES = 1_000_000

    // — Domain ⇄ file ——————————————————————————————————————————————————————

    private fun toArchive(place: Place) = ArchivePlace(
        name = place.name, code = place.code, city = place.city, country = place.country,
        lat = place.latitude, lon = place.longitude, zone = place.timeZoneId, address = place.address,
    )

    private fun fromArchive(place: ArchivePlace) = Place(
        name = place.name, code = place.code, city = place.city, country = place.country,
        latitude = place.lat, longitude = place.lon, timeZoneId = place.zone, address = place.address,
    )

    private fun toArchive(traveler: Traveler) = ArchiveTraveler(
        id = traveler.id,
        fullName = traveler.fullName,
        nickname = traveler.nickname,
        seat = traveler.seatPreference.name,
        meal = traveler.mealPreference,
        loyalty = traveler.loyaltyRefs,
        phone = traveler.contactPhone,
    )

    private fun fromArchive(traveler: ArchiveTraveler) = Traveler(
        id = traveler.id,
        fullName = traveler.fullName,
        nickname = traveler.nickname,
        seatPreference = SeatPreference.entries.firstOrNull { it.name == traveler.seat } ?: SeatPreference.NONE,
        mealPreference = traveler.meal,
        loyaltyRefs = traveler.loyalty,
        contactPhone = traveler.phone,
    )

    private fun toArchive(segment: Segment): ArchiveSegment {
        val base = ArchiveSegment(
            id = segment.id,
            kind = segment.kind.name,
            start = segment.startEpochMillis,
            end = segment.endEpochMillis,
            startZone = segment.startZoneId,
            endZone = segment.endZoneId,
            travelers = segment.travelerIds.toList(),
            reference = segment.confirmationCode,
            bookedWith = segment.bookedWith,
            note = segment.note,
            origin = toArchive(segment.origin),
            destination = toArchive(segment.destination),
        )
        return when (segment) {
            is Segment.Flight -> base.copy(
                carrier = segment.carrierCode, number = segment.flightNumber,
                departureTerminal = segment.departureTerminal, departureGate = segment.departureGate,
                arrivalTerminal = segment.arrivalTerminal, arrivalGate = segment.arrivalGate,
                aircraft = segment.aircraft, cabin = segment.cabin, seats = segment.seats,
                tickets = segment.ticketNumbers, operatedBy = segment.operatedBy,
            )
            is Segment.Lodging -> base.copy(
                name = segment.propertyName, room = segment.roomDescription, checkInNote = segment.checkInNote,
            )
            is Segment.Ground -> base.copy(
                mode = segment.mode.name, provider = segment.provider, pickup = segment.pickupInstruction,
            )
            is Segment.Experience -> base.copy(
                name = segment.name, category = segment.category, tips = segment.curatedBy,
            )
        }
    }

    /** Null for a kind this version does not know, rather than a guess at one. */
    private fun fromArchive(row: ArchiveSegment, tripId: String): Segment? {
        val origin = fromArchive(row.origin)
        val destination = row.destination?.let(::fromArchive) ?: origin
        val travelers = row.travelers.toSet()
        return when (row.kind) {
            "FLIGHT" -> Segment.Flight(
                id = row.id, tripId = tripId,
                carrierCode = row.carrier.orEmpty(), flightNumber = row.number.orEmpty(),
                origin = origin, destination = destination,
                startEpochMillis = row.start, endEpochMillis = row.end,
                startZoneId = row.startZone, endZoneId = row.endZone, travelerIds = travelers,
                confirmationCode = row.reference, bookedWith = row.bookedWith, note = row.note,
                ticketNumbers = row.tickets, departureTerminal = row.departureTerminal,
                departureGate = row.departureGate, arrivalTerminal = row.arrivalTerminal,
                arrivalGate = row.arrivalGate, aircraft = row.aircraft, cabin = row.cabin,
                seats = row.seats, operatedBy = row.operatedBy,
            )
            "LODGING" -> Segment.Lodging(
                id = row.id, tripId = tripId, propertyName = row.name ?: origin.name, origin = origin,
                startEpochMillis = row.start, endEpochMillis = row.end,
                startZoneId = row.startZone, endZoneId = row.endZone, travelerIds = travelers,
                confirmationCode = row.reference, bookedWith = row.bookedWith, note = row.note,
                roomDescription = row.room, checkInNote = row.checkInNote,
            )
            "GROUND" -> Segment.Ground(
                id = row.id, tripId = tripId,
                mode = GroundMode.entries.firstOrNull { it.name == row.mode } ?: GroundMode.TAXI,
                origin = origin, destination = destination,
                startEpochMillis = row.start, endEpochMillis = row.end,
                startZoneId = row.startZone, endZoneId = row.endZone, travelerIds = travelers,
                confirmationCode = row.reference, bookedWith = row.bookedWith, note = row.note,
                provider = row.provider, pickupInstruction = row.pickup,
            )
            "EXPERIENCE" -> Segment.Experience(
                id = row.id, tripId = tripId, name = row.name ?: origin.name,
                category = row.category.orEmpty(), origin = origin,
                startEpochMillis = row.start, endEpochMillis = row.end,
                startZoneId = row.startZone, endZoneId = row.endZone, travelerIds = travelers,
                confirmationCode = row.reference, bookedWith = row.bookedWith, note = row.note,
                curatedBy = row.tips,
            )
            else -> null
        }
    }

    private fun toArchive(idea: Idea) = ArchiveIdea(
        id = idea.id, title = idea.title, kind = idea.kind.name, city = idea.city,
        place = idea.place?.let(::toArchive), note = idea.note, price = idea.priceBand?.name,
        minutes = idea.typicalMinutes, bestTime = idea.bestTime,
        plannedDay = idea.plannedDate?.toString(), interested = idea.interestedTravelerIds.toList(),
        status = idea.status.name, scheduledSegmentId = idea.scheduledSegmentId, added = idea.addedAtMillis,
    )

    private fun fromArchive(row: ArchiveIdea, tripId: String): Idea? {
        val kind = IdeaKind.entries.firstOrNull { it.name == row.kind } ?: return null
        return Idea(
            id = row.id, tripId = tripId, title = row.title, kind = kind, city = row.city,
            place = row.place?.let(::fromArchive), note = row.note,
            priceBand = PriceBand.entries.firstOrNull { it.name == row.price },
            typicalMinutes = row.minutes, bestTime = row.bestTime,
            plannedDate = row.plannedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            interestedTravelerIds = row.interested.toSet(),
            status = IdeaStatus.entries.firstOrNull { it.name == row.status } ?: IdeaStatus.SAVED,
            scheduledSegmentId = row.scheduledSegmentId, addedAtMillis = row.added,
        )
    }
}

@Serializable
data class WaymarkArchive(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val travelers: List<ArchiveTraveler> = emptyList(),
    val trips: List<ArchiveTrip> = emptyList(),
)

@Serializable
data class ArchiveTrip(
    val id: String,
    val name: String,
    val destinationSummary: String = "",
    val start: Long,
    val end: Long,
    val homeZone: String,
    val archived: Boolean = false,
    /** Traveler ids, in the party's order. */
    val party: List<String> = emptyList(),
    val segments: List<ArchiveSegment> = emptyList(),
    val ideas: List<ArchiveIdea> = emptyList(),
)

@Serializable
data class ArchiveTraveler(
    val id: String,
    val fullName: String,
    val nickname: String? = null,
    val seat: String = "NONE",
    val meal: String? = null,
    val loyalty: List<String> = emptyList(),
    val phone: String? = null,
)

@Serializable
data class ArchivePlace(
    /** Empty only in a code, where a station travels as its [code] alone. */
    val name: String = "",
    val code: String? = null,
    val city: String = "",
    val country: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val zone: String = "UTC",
    val address: String? = null,
)

/** Every kind of booking in one flat shape; [kind] says which fields mean anything. */
@Serializable
data class ArchiveSegment(
    val id: String,
    val kind: String,
    val start: Long,
    val end: Long,
    /** Empty only in a code, where it is the origin's zone. */
    val startZone: String = "",
    val endZone: String = "",
    val travelers: List<String> = emptyList(),
    val reference: String? = null,
    val bookedWith: String? = null,
    val note: String? = null,
    val origin: ArchivePlace,
    val destination: ArchivePlace? = null,
    val carrier: String? = null,
    val number: String? = null,
    val departureTerminal: String? = null,
    val departureGate: String? = null,
    val arrivalTerminal: String? = null,
    val arrivalGate: String? = null,
    val aircraft: String? = null,
    val cabin: String? = null,
    val seats: Map<String, String> = emptyMap(),
    val tickets: Map<String, String> = emptyMap(),
    val operatedBy: String? = null,
    /** Property name for a stay; the booking's name for an experience. */
    val name: String? = null,
    val room: String? = null,
    val checkInNote: String? = null,
    val mode: String? = null,
    val provider: String? = null,
    val pickup: String? = null,
    val category: String? = null,
    val tips: String? = null,
)

@Serializable
data class ArchiveIdea(
    val id: String,
    val title: String,
    val kind: String,
    val city: String = "",
    val place: ArchivePlace? = null,
    val note: String? = null,
    val price: String? = null,
    val minutes: Int? = null,
    val bestTime: String? = null,
    val plannedDay: String? = null,
    val interested: List<String> = emptyList(),
    val status: String = "SAVED",
    val scheduledSegmentId: String? = null,
    val added: Long = 0L,
)
