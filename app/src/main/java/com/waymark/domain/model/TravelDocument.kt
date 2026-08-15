package com.waymark.domain.model

import java.time.LocalDate

/**
 * A passport, a visa, an insurance policy. The number is held sealed like
 * every other secret; the dates are held in the clear because the app has to
 * reason about them — an expiry warning that needs authentication to fire is
 * a warning that arrives at the airport.
 */
data class TravelDocument(
    val id: String,
    val travelerId: String,
    val kind: DocumentKind,
    val label: String,
    val number: String,
    val issuer: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val note: String? = null,
    val fileUri: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    /** What the vault shows before the reader authenticates. */
    val maskedNumber: String
        get() = when {
            number.length <= 4 -> "•".repeat(number.length.coerceAtLeast(2))
            else -> "•".repeat(number.length - 4) + number.takeLast(4)
        }
}

enum class DocumentKind {
    PASSPORT, VISA, ID_CARD, INSURANCE, VACCINATION, DRIVING_PERMIT, TICKET, OTHER;

    val label: String
        get() = when (this) {
            PASSPORT -> "Passport"
            VISA -> "Visa"
            ID_CARD -> "ID card"
            INSURANCE -> "Insurance"
            VACCINATION -> "Vaccination record"
            DRIVING_PERMIT -> "Driving permit"
            TICKET -> "Ticket"
            OTHER -> "Document"
        }

    /**
     * Passports are the only document most countries judge against a margin
     * rather than a date: many require validity well beyond the return flight.
     */
    val hasValidityMargin: Boolean get() = this == PASSPORT
}
