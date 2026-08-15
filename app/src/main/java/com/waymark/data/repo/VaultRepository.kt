package com.waymark.data.repo

import com.waymark.data.local.Mappers
import com.waymark.data.local.SecretCipher
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.Bcbp
import com.waymark.domain.model.BoardingPass
import com.waymark.domain.model.Reservation
import com.waymark.domain.model.Secret
import com.waymark.domain.model.SecretField
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/**
 * Confirmation codes, ticket numbers and boarding passes.
 *
 * Everything sensitive is sealed on the way in and opened on the way out, so
 * the only plaintext copy lives for as long as the screen showing it.
 */
class VaultRepository(
    database: WaymarkDatabase,
    private val cipher: SecretCipher,
) {

    private val reservations = database.reservationDao()
    private val passes = database.boardingPassDao()

    fun observeReservations(tripId: String): Flow<List<Reservation>> =
        reservations.observeForTrip(tripId).map { rows ->
            rows.map { Mappers.toReservation(it, cipher) }
        }

    fun observePasses(tripId: String): Flow<List<BoardingPass>> =
        passes.observeForTrip(tripId).map { rows ->
            rows.map { Mappers.toBoardingPass(it, cipher) }
        }

    fun observePassesForSegment(segmentId: String): Flow<List<BoardingPass>> =
        passes.observeForSegment(segmentId).map { rows ->
            rows.map { Mappers.toBoardingPass(it, cipher) }
        }

    suspend fun findReservation(id: String): Reservation? =
        reservations.find(id)?.let { Mappers.toReservation(it, cipher) }

    suspend fun findReservationForSegment(segmentId: String): Reservation? =
        reservations.findForSegment(segmentId)?.let { Mappers.toReservation(it, cipher) }

    suspend fun save(reservation: Reservation) =
        reservations.upsert(Mappers.toEntity(reservation, cipher))

    suspend fun delete(reservationId: String) = reservations.delete(reservationId)

    /**
     * Create the vault record that belongs to a segment the traveler just
     * added. Blank fields are dropped rather than stored empty.
     */
    suspend fun recordFor(
        segment: Segment,
        label: String,
        vendor: String,
        confirmationCode: String?,
        eTicketNumbers: Map<String, String> = emptyMap(),
        extraSecrets: List<Secret> = emptyList(),
    ): Reservation {
        val secrets = buildList {
            confirmationCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(Secret(SecretField.CONFIRMATION_CODE, it.uppercase()))
            }
            eTicketNumbers.forEach { (travelerId, number) ->
                number.trim().takeIf { it.isNotEmpty() }?.let {
                    add(Secret(SecretField.ETICKET_NUMBER, it, travelerId))
                }
            }
            addAll(extraSecrets.filter { it.value.isNotBlank() })
        }
        val reservation = Reservation(
            id = newId("res"),
            tripId = segment.tripId,
            segmentId = segment.id,
            label = label,
            vendor = vendor,
            kind = segment.kind,
            travelerIds = segment.travelerIds,
            secrets = secrets,
        )
        save(reservation)
        return reservation
    }

    suspend fun savePass(pass: BoardingPass) = passes.upsert(Mappers.toEntity(pass, cipher))

    suspend fun deletePass(passId: String) = passes.delete(passId)

    /**
     * Build a pass for a flight the traveler has checked in for by hand. The
     * BCBP payload is composed locally so the pass still renders — and still
     * carries the right data — with no network.
     */
    suspend fun issuePass(
        flight: Segment.Flight,
        travelerId: String,
        passengerName: String,
        seat: String?,
        sequenceNumber: String?,
        boardingGroup: String?,
        recordLocator: String,
        fastTrack: Boolean = false,
        imageUri: String? = null,
    ): BoardingPass {
        val zone = runCatching { ZoneId.of(flight.startZoneId) }.getOrDefault(ZoneId.systemDefault())
        val date = java.time.Instant.ofEpochMilli(flight.startEpochMillis).atZone(zone).toLocalDate()
        val payload = Bcbp.build(
            passengerName = passengerName,
            recordLocator = recordLocator,
            origin = flight.origin.code ?: flight.origin.shortLabel,
            destination = flight.destination.code ?: flight.destination.shortLabel,
            carrier = flight.carrierCode,
            flightNumber = flight.flightNumber,
            date = date,
            cabin = flight.cabin?.take(1) ?: "Y",
            seat = seat,
            sequence = sequenceNumber,
        )
        val pass = BoardingPass(
            id = newId("pass"),
            tripId = flight.tripId,
            segmentId = flight.id,
            travelerId = travelerId,
            passengerName = passengerName,
            designator = flight.designator,
            origin = flight.origin.code ?: flight.origin.shortLabel,
            destination = flight.destination.code ?: flight.destination.shortLabel,
            seat = seat,
            boardingGroup = boardingGroup,
            sequenceNumber = sequenceNumber,
            gate = flight.departureGate,
            boardingTimeMillis = Bcbp.defaultBoardingTime(flight.startEpochMillis, zone),
            cabin = flight.cabin,
            fastTrack = fastTrack,
            barcodePayload = payload,
            imageUri = imageUri,
        )
        savePass(pass)
        return pass
    }

    /** Import a pass captured from an airline app or email. */
    suspend fun importPass(
        tripId: String,
        segment: Segment.Flight,
        travelerId: String,
        payload: String,
        imageUri: String?,
    ): BoardingPass? {
        val parsed = Bcbp.parse(payload) ?: return null
        val pass = BoardingPass(
            id = newId("pass"),
            tripId = tripId,
            segmentId = segment.id,
            travelerId = travelerId,
            passengerName = parsed.passengerName,
            designator = "${parsed.carrier.trim()}${parsed.flightNumber}",
            origin = parsed.origin,
            destination = parsed.destination,
            seat = parsed.seat.ifBlank { null },
            boardingGroup = null,
            sequenceNumber = parsed.sequence.ifBlank { null },
            gate = segment.departureGate,
            boardingTimeMillis = Bcbp.defaultBoardingTime(segment.startEpochMillis),
            cabin = parsed.cabin,
            fastTrack = false,
            barcodePayload = payload,
            imageUri = imageUri,
        )
        savePass(pass)
        return pass
    }

    /** Reservations grouped the way the vault screen lists them. */
    fun observeGrouped(tripId: String): Flow<Map<SegmentKind, List<Reservation>>> =
        observeReservations(tripId).map { list -> list.groupBy { it.kind } }

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}"
}
