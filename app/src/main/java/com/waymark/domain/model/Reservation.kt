package com.waymark.domain.model

/**
 * A vault record. Everything in [secrets] is written to disk encrypted and is
 * only decrypted on demand, so a database dump yields nothing readable.
 */
data class Reservation(
    val id: String,
    val tripId: String,
    val segmentId: String?,
    val label: String,
    val vendor: String,
    val kind: SegmentKind,
    val travelerIds: Set<String> = emptySet(),
    val secrets: List<Secret> = emptyList(),
    val documentUri: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun secret(field: SecretField): Secret? = secrets.firstOrNull { it.field == field }
    val confirmationCode: String? get() = secret(SecretField.CONFIRMATION_CODE)?.value
}

enum class SecretField {
    CONFIRMATION_CODE, ETICKET_NUMBER, RECORD_LOCATOR, LOYALTY_NUMBER,
    DOCUMENT_NUMBER, PIN, GATE_PASS, OTHER;

    val label: String
        get() = when (this) {
            CONFIRMATION_CODE -> "Confirmation code"
            ETICKET_NUMBER -> "E-ticket number"
            RECORD_LOCATOR -> "Record locator"
            LOYALTY_NUMBER -> "Loyalty number"
            DOCUMENT_NUMBER -> "Document number"
            PIN -> "PIN"
            GATE_PASS -> "Gate pass"
            OTHER -> "Reference"
        }
}

data class Secret(
    val field: SecretField,
    val value: String,
    val travelerId: String? = null,
) {
    /** What the vault shows before the reader authenticates. */
    val masked: String
        get() = when {
            value.length <= 2 -> "••"
            value.length <= 6 -> value.take(1) + "•".repeat(value.length - 1)
            else -> value.take(2) + "•".repeat(value.length - 4) + value.takeLast(2)
        }
}

/**
 * A boarding pass held for offline use. [barcodePayload] is the IATA BCBP
 * string as issued; [imageUri] points at an imported pass image when the
 * traveler has one, which is preferred over the rendered barcode.
 */
data class BoardingPass(
    val id: String,
    val tripId: String,
    val segmentId: String,
    val travelerId: String,
    val passengerName: String,
    val designator: String,
    val origin: String,
    val destination: String,
    val seat: String?,
    val boardingGroup: String?,
    val sequenceNumber: String?,
    val gate: String?,
    val boardingTimeMillis: Long?,
    val cabin: String?,
    val fastTrack: Boolean = false,
    val barcodePayload: String,
    val imageUri: String? = null,
    val addedAtMillis: Long = System.currentTimeMillis(),
)
