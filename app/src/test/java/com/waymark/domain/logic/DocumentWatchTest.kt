package com.waymark.domain.logic

import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.TravelDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocumentWatchTest {

    private val today = LocalDate.of(2026, 5, 1)
    private val tripEnd = LocalDate.of(2026, 5, 24)

    private fun document(
        kind: DocumentKind,
        expires: LocalDate?,
        id: String = "doc",
    ) = TravelDocument(
        id = id,
        travelerId = "trav-mara",
        kind = kind,
        label = kind.label,
        number = "123456789",
        expiresOn = expires,
    )

    @Test
    fun `a passport expiring before the return flight is urgent`() {
        val verdict = DocumentWatch.assess(
            document(DocumentKind.PASSPORT, LocalDate.of(2026, 5, 20)),
            tripEnd,
            today,
        )
        assertEquals(DocumentUrgency.URGENT, verdict.urgency)
        assertTrue(verdict.needsAttention)
    }

    @Test
    fun `an expired document says how long ago`() {
        val verdict = DocumentWatch.assess(
            document(DocumentKind.PASSPORT, today.minusDays(10)),
            tripEnd,
            today,
        )
        assertEquals(DocumentUrgency.EXPIRED, verdict.urgency)
        assertEquals(-10L, verdict.daysRemaining)
        assertTrue(verdict.detail.contains("10 days ago"))
    }

    /**
     * The rule this feature exists for: a passport valid past the trip is
     * still a problem if it is inside the six-month margin most borders ask
     * for, and that is not something a plain expiry date tells you.
     */
    @Test
    fun `a passport inside the six-month margin is flagged even though it is in date`() {
        val expiry = tripEnd.plusMonths(3)
        val verdict = DocumentWatch.assess(document(DocumentKind.PASSPORT, expiry), tripEnd, today)

        assertEquals(DocumentUrgency.INSUFFICIENT_MARGIN, verdict.urgency)
        assertTrue(verdict.daysRemaining!! > 0)
        assertTrue(verdict.detail.contains("six months"))
    }

    @Test
    fun `the margin applies to passports only`() {
        val expiry = tripEnd.plusMonths(3)
        val insurance = DocumentWatch.assess(document(DocumentKind.INSURANCE, expiry), tripEnd, today)
        assertEquals(DocumentUrgency.FINE, insurance.urgency)
    }

    @Test
    fun `a passport comfortably past the margin is fine`() {
        val verdict = DocumentWatch.assess(
            document(DocumentKind.PASSPORT, LocalDate.of(2031, 4, 12)),
            tripEnd,
            today,
        )
        assertEquals(DocumentUrgency.FINE, verdict.urgency)
        assertTrue(!verdict.needsAttention)
    }

    @Test
    fun `a document expiring within three months is worth mentioning`() {
        val verdict = DocumentWatch.assess(
            document(DocumentKind.INSURANCE, today.plusDays(40)),
            tripEnd,
            today,
        )
        assertEquals(DocumentUrgency.SOON, verdict.urgency)
    }

    @Test
    fun `no expiry recorded is not an error`() {
        val verdict = DocumentWatch.assess(document(DocumentKind.ID_CARD, null), tripEnd, today)
        assertEquals(DocumentUrgency.FINE, verdict.urgency)
        assertEquals(null, verdict.daysRemaining)
    }

    @Test
    fun `with no trip dates a document is judged on its own expiry alone`() {
        val soon = DocumentWatch.assess(
            document(DocumentKind.PASSPORT, today.plusDays(30)),
            tripEnd = null,
            today = today,
        )
        assertEquals(DocumentUrgency.SOON, soon.urgency)
    }

    @Test
    fun `the worst problem sorts to the top`() {
        val verdicts = DocumentWatch.assessAll(
            listOf(
                document(DocumentKind.INSURANCE, today.plusDays(40), id = "soon"),
                document(DocumentKind.PASSPORT, today.minusDays(1), id = "expired"),
                document(DocumentKind.PASSPORT, tripEnd.plusMonths(2), id = "margin"),
                document(DocumentKind.VISA, LocalDate.of(2031, 1, 1), id = "fine"),
            ),
            tripEnd,
            today,
        )
        assertEquals(
            listOf("expired", "margin", "soon", "fine"),
            verdicts.map { it.document.id },
        )
    }

    @Test
    fun `a traveler with no passport at all is named`() {
        val missing = DocumentWatch.travelersMissingPassport(
            travelerIds = listOf("trav-mara", "trav-julian"),
            documents = listOf(document(DocumentKind.PASSPORT, LocalDate.of(2031, 1, 1))),
        )
        assertEquals(listOf("trav-julian"), missing)
    }
}
