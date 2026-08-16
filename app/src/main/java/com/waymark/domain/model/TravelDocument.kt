package com.waymark.domain.model

import java.time.LocalDate

/**
 * A passport, a visa, an insurance policy.
 *
 * The number used to be sealed and shown masked. The dates were always in the
 * clear, because an expiry warning that needs authentication before it can fire
 * is a warning that arrives at the airport — and once that was true of the
 * dates, the ceremony around the number was buying nothing.
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
)

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
