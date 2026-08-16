package com.waymark.domain.model

/**
 * Something the app wants to put in front of the traveler.
 *
 * With no feed to watch, there is exactly one source of these: the departure
 * reminder, raised from the itinerary and the clock. It is deduplicated on a
 * signature so a flight is announced once, not once per check.
 */
data class DisruptionAlert(
    val segmentId: String,
    val designator: String,
    val headline: String,
    val detail: String,
    val severity: Severity,
    val raisedAtMillis: Long = System.currentTimeMillis(),
) {
    enum class Severity { NOTICE, WARNING, CRITICAL }
}
