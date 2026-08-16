package com.waymark.domain.model

/**
 * Something the app wants to put in front of the traveler.
 *
 * With no feed to watch, there is exactly one source of these: the reminder
 * that a booking is about to start, raised from the itinerary and the clock. It
 * is deduplicated on a signature so a booking is announced once, not once per
 * check.
 */
data class DisruptionAlert(
    val segmentId: String,
    /**
     * What the booking is called — `BA286`, `The Bloomsbury Rooms`. The
     * database column is still named `designator`, from when the only thing
     * this could describe was a flight; renaming it would cost a migration to
     * buy nothing.
     */
    val label: String,
    val headline: String,
    val detail: String,
    val severity: Severity,
    val raisedAtMillis: Long = System.currentTimeMillis(),
) {
    enum class Severity { NOTICE, WARNING, CRITICAL }
}
