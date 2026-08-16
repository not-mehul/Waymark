package com.waymark.data.local

import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.PriceBand
import com.waymark.domain.model.SeatPreference
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.Trip

/** Row ⇄ model. Nothing here encrypts: the app stores what it is given. */
object Mappers {

    fun toTrip(entity: TripEntity): Trip = Trip(
        id = entity.id,
        name = entity.name,
        destinationSummary = entity.destinationSummary,
        startEpochMillis = entity.startEpochMillis,
        endEpochMillis = entity.endEpochMillis,
        homeZoneId = entity.homeZoneId,
        coverPlace = Codecs.decodePlace(entity.coverPlace),
        archived = entity.archived,
    )

    fun toEntity(trip: Trip): TripEntity = TripEntity(
        id = trip.id,
        name = trip.name,
        destinationSummary = trip.destinationSummary,
        startEpochMillis = trip.startEpochMillis,
        endEpochMillis = trip.endEpochMillis,
        homeZoneId = trip.homeZoneId,
        coverPlace = Codecs.encodePlace(trip.coverPlace).ifBlank { null },
        archived = trip.archived,
    )

    fun toTraveler(entity: TravelerEntity): Traveler = Traveler(
        id = entity.id,
        fullName = entity.fullName,
        nickname = entity.nickname,
        seatPreference = runCatching { SeatPreference.valueOf(entity.seatPreference) }
            .getOrDefault(SeatPreference.NONE),
        mealPreference = entity.mealPreference,
        loyaltyRefs = Codecs.decodeList(entity.loyaltyRefs),
        documentRef = entity.documentRef,
        contactPhone = entity.contactPhone,
    )

    fun toEntity(traveler: Traveler): TravelerEntity = TravelerEntity(
        id = traveler.id,
        fullName = traveler.fullName,
        nickname = traveler.nickname,
        seatPreference = traveler.seatPreference.name,
        mealPreference = traveler.mealPreference,
        loyaltyRefs = Codecs.encodeList(traveler.loyaltyRefs).ifBlank { null },
        documentRef = traveler.documentRef,
        contactPhone = traveler.contactPhone,
    )

    fun toSegment(entity: SegmentEntity): Segment {
        val origin = Codecs.decodePlace(entity.originPlace) ?: Place(name = "Unknown")
        val destination = Codecs.decodePlace(entity.destinationPlace) ?: origin
        val travelerIds = Codecs.decodeIds(entity.travelerIds)
        return when (runCatching { SegmentKind.valueOf(entity.kind) }.getOrDefault(SegmentKind.EXPERIENCE)) {
            SegmentKind.FLIGHT -> Segment.Flight(
                id = entity.id,
                tripId = entity.tripId,
                carrierCode = entity.carrierCode.orEmpty(),
                flightNumber = entity.flightNumber.orEmpty(),
                origin = origin,
                destination = destination,
                startEpochMillis = entity.startEpochMillis,
                endEpochMillis = entity.endEpochMillis,
                startZoneId = entity.startZoneId,
                endZoneId = entity.endZoneId,
                travelerIds = travelerIds,
                confirmationCode = entity.confirmationCode,
                bookedWith = entity.bookedWith,
                note = entity.note,
                ticketNumbers = Codecs.decodeMap(entity.ticketNumbers),
                departureTerminal = entity.departureTerminal,
                departureGate = entity.departureGate,
                arrivalTerminal = entity.arrivalTerminal,
                arrivalGate = entity.arrivalGate,
                aircraft = entity.aircraft,
                cabin = entity.cabin,
                seats = Codecs.decodeMap(entity.seats),
                operatedBy = entity.operatedBy,
            )

            SegmentKind.LODGING -> Segment.Lodging(
                id = entity.id,
                tripId = entity.tripId,
                propertyName = entity.propertyName ?: origin.name,
                origin = origin,
                startEpochMillis = entity.startEpochMillis,
                endEpochMillis = entity.endEpochMillis,
                startZoneId = entity.startZoneId,
                endZoneId = entity.endZoneId,
                travelerIds = travelerIds,
                confirmationCode = entity.confirmationCode,
                bookedWith = entity.bookedWith,
                note = entity.note,
                roomDescription = entity.roomDescription,
                checkInNote = entity.checkInNote,
            )

            SegmentKind.GROUND -> Segment.Ground(
                id = entity.id,
                tripId = entity.tripId,
                mode = runCatching { GroundMode.valueOf(entity.groundMode.orEmpty()) }
                    .getOrDefault(GroundMode.TAXI),
                origin = origin,
                destination = destination,
                startEpochMillis = entity.startEpochMillis,
                endEpochMillis = entity.endEpochMillis,
                startZoneId = entity.startZoneId,
                endZoneId = entity.endZoneId,
                travelerIds = travelerIds,
                confirmationCode = entity.confirmationCode,
                bookedWith = entity.bookedWith,
                note = entity.note,
                provider = entity.provider,
                pickupInstruction = entity.pickupInstruction,
            )

            SegmentKind.EXPERIENCE -> Segment.Experience(
                id = entity.id,
                tripId = entity.tripId,
                name = entity.experienceName ?: origin.name,
                category = entity.category.orEmpty(),
                origin = origin,
                startEpochMillis = entity.startEpochMillis,
                endEpochMillis = entity.endEpochMillis,
                startZoneId = entity.startZoneId,
                endZoneId = entity.endZoneId,
                travelerIds = travelerIds,
                confirmationCode = entity.confirmationCode,
                bookedWith = entity.bookedWith,
                note = entity.note,
                curatedBy = entity.curatedBy,
            )
        }
    }

    fun toEntity(segment: Segment): SegmentEntity {
        val base = SegmentEntity(
            id = segment.id,
            tripId = segment.tripId,
            kind = segment.kind.name,
            startEpochMillis = segment.startEpochMillis,
            endEpochMillis = segment.endEpochMillis,
            startZoneId = segment.startZoneId,
            endZoneId = segment.endZoneId,
            travelerIds = Codecs.encodeIds(segment.travelerIds).ifBlank { null },
            confirmationCode = segment.confirmationCode,
            bookedWith = segment.bookedWith,
            note = segment.note,
            originPlace = Codecs.encodePlace(segment.origin),
            destinationPlace = Codecs.encodePlace(segment.destination),
        )
        return when (segment) {
            is Segment.Flight -> base.copy(
                carrierCode = segment.carrierCode,
                flightNumber = segment.flightNumber,
                departureTerminal = segment.departureTerminal,
                departureGate = segment.departureGate,
                arrivalTerminal = segment.arrivalTerminal,
                arrivalGate = segment.arrivalGate,
                aircraft = segment.aircraft,
                cabin = segment.cabin,
                seats = Codecs.encodeMap(segment.seats).ifBlank { null },
                ticketNumbers = Codecs.encodeMap(segment.ticketNumbers).ifBlank { null },
                operatedBy = segment.operatedBy,
            )

            is Segment.Lodging -> base.copy(
                propertyName = segment.propertyName,
                roomDescription = segment.roomDescription,
                checkInNote = segment.checkInNote,
            )

            is Segment.Ground -> base.copy(
                groundMode = segment.mode.name,
                provider = segment.provider,
                pickupInstruction = segment.pickupInstruction,
            )

            is Segment.Experience -> base.copy(
                experienceName = segment.name,
                category = segment.category,
                curatedBy = segment.curatedBy,
            )
        }
    }

    fun toIdea(entity: IdeaEntity): Idea = Idea(
        id = entity.id,
        tripId = entity.tripId,
        title = entity.title,
        kind = runCatching { IdeaKind.valueOf(entity.kind) }.getOrDefault(IdeaKind.SEE),
        city = entity.city,
        place = Codecs.decodePlace(entity.place),
        note = entity.note,
        priceBand = entity.priceBand?.let { band ->
            runCatching { PriceBand.valueOf(band) }.getOrNull()
        },
        typicalMinutes = entity.typicalMinutes,
        bestTime = entity.bestTime,
        plannedDate = entity.plannedDateEpochDay?.let(java.time.LocalDate::ofEpochDay),
        interestedTravelerIds = Codecs.decodeIds(entity.interestedTravelerIds),
        status = runCatching { IdeaStatus.valueOf(entity.status) }.getOrDefault(IdeaStatus.SAVED),
        scheduledSegmentId = entity.scheduledSegmentId,
        source = entity.source,
        addedAtMillis = entity.addedAtMillis,
    )

    fun toEntity(idea: Idea): IdeaEntity = IdeaEntity(
        id = idea.id,
        tripId = idea.tripId,
        title = idea.title,
        kind = idea.kind.name,
        city = idea.city,
        place = Codecs.encodePlace(idea.place).ifBlank { null },
        note = idea.note,
        priceBand = idea.priceBand?.name,
        typicalMinutes = idea.typicalMinutes,
        bestTime = idea.bestTime,
        plannedDateEpochDay = idea.plannedDate?.toEpochDay(),
        interestedTravelerIds = Codecs.encodeIds(idea.interestedTravelerIds).ifBlank { null },
        status = idea.status.name,
        scheduledSegmentId = idea.scheduledSegmentId,
        source = idea.source,
        addedAtMillis = idea.addedAtMillis,
    )

    fun toAlert(entity: RaisedAlertEntity): DisruptionAlert = DisruptionAlert(
        segmentId = entity.segmentId,
        label = entity.designator,
        headline = entity.headline,
        detail = entity.detail,
        severity = runCatching { DisruptionAlert.Severity.valueOf(entity.severity) }
            .getOrDefault(DisruptionAlert.Severity.NOTICE),
        raisedAtMillis = entity.raisedAtMillis,
    )
}
