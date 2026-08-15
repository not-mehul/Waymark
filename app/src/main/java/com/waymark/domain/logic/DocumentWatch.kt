package com.waymark.domain.logic

import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.TravelDocument
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DocumentUrgency { FINE, SOON, URGENT, EXPIRED, INSUFFICIENT_MARGIN;

    val label: String
        get() = when (this) {
            FINE -> "Valid"
            SOON -> "Expires soon"
            URGENT -> "Expires before you return"
            EXPIRED -> "Expired"
            INSUFFICIENT_MARGIN -> "Too little validity left"
        }
}

data class DocumentVerdict(
    val document: TravelDocument,
    val urgency: DocumentUrgency,
    val daysRemaining: Long?,
    val detail: String,
) {
    val needsAttention: Boolean get() = urgency != DocumentUrgency.FINE
}

/**
 * Whether the documents a trip depends on will still be valid when it happens.
 *
 * The interesting rule is not expiry — it is the margin. Most destinations
 * require a passport valid for six months beyond arrival, so a passport that
 * expires four months after the return flight is a problem the traveler finds
 * out about at check-in unless something says so first.
 */
object DocumentWatch {

    /** The common requirement. Some countries ask three months; six is the safe line. */
    const val PASSPORT_MARGIN_MONTHS = 6L

    /** Anything inside this window is worth mentioning even for a distant trip. */
    private const val SOON_DAYS = 90L

    fun assess(
        document: TravelDocument,
        tripEnd: LocalDate?,
        today: LocalDate = LocalDate.now(),
    ): DocumentVerdict {
        val expiry = document.expiresOn
            ?: return DocumentVerdict(
                document = document,
                urgency = DocumentUrgency.FINE,
                daysRemaining = null,
                detail = "No expiry recorded.",
            )

        val daysRemaining = ChronoUnit.DAYS.between(today, expiry)

        if (daysRemaining < 0) {
            return DocumentVerdict(
                document = document,
                urgency = DocumentUrgency.EXPIRED,
                daysRemaining = daysRemaining,
                detail = "Expired ${-daysRemaining} days ago.",
            )
        }

        // Does it survive the trip at all?
        if (tripEnd != null && expiry.isBefore(tripEnd)) {
            return DocumentVerdict(
                document = document,
                urgency = DocumentUrgency.URGENT,
                daysRemaining = daysRemaining,
                detail = "Expires on $expiry, before the trip ends on $tripEnd.",
            )
        }

        // Passports are judged against the margin, not the date.
        if (document.kind.hasValidityMargin && tripEnd != null) {
            val requiredUntil = tripEnd.plusMonths(PASSPORT_MARGIN_MONTHS)
            if (expiry.isBefore(requiredUntil)) {
                val short = ChronoUnit.DAYS.between(expiry, requiredUntil)
                return DocumentVerdict(
                    document = document,
                    urgency = DocumentUrgency.INSUFFICIENT_MARGIN,
                    daysRemaining = daysRemaining,
                    detail = "Many countries require six months' validity beyond arrival. " +
                        "This is $short days short of that on the return date.",
                )
            }
        }

        if (daysRemaining <= SOON_DAYS) {
            return DocumentVerdict(
                document = document,
                urgency = DocumentUrgency.SOON,
                daysRemaining = daysRemaining,
                detail = "Expires in $daysRemaining days. Renewals take weeks.",
            )
        }

        return DocumentVerdict(
            document = document,
            urgency = DocumentUrgency.FINE,
            daysRemaining = daysRemaining,
            detail = "Valid until $expiry.",
        )
    }

    fun assessAll(
        documents: List<TravelDocument>,
        tripEnd: LocalDate?,
        today: LocalDate = LocalDate.now(),
    ): List<DocumentVerdict> = documents
        .map { assess(it, tripEnd, today) }
        .sortedWith(compareBy({ severityRank(it.urgency) }, { it.daysRemaining ?: Long.MAX_VALUE }))

    private fun severityRank(urgency: DocumentUrgency): Int = when (urgency) {
        DocumentUrgency.EXPIRED -> 0
        DocumentUrgency.URGENT -> 1
        DocumentUrgency.INSUFFICIENT_MARGIN -> 2
        DocumentUrgency.SOON -> 3
        DocumentUrgency.FINE -> 4
    }

    /**
     * Travelers on the trip with no passport recorded at all. Silence about a
     * missing document is worse than a warning about an expiring one.
     */
    fun travelersMissingPassport(
        travelerIds: Collection<String>,
        documents: List<TravelDocument>,
    ): List<String> {
        val held = documents
            .filter { it.kind == DocumentKind.PASSPORT }
            .map { it.travelerId }
            .toSet()
        return travelerIds.filterNot { it in held }
    }
}
