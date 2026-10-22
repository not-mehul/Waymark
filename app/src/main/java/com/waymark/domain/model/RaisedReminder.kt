package com.waymark.domain.model

/**
 * A reminder the app has raised: something booked is about to start.
 *
 * With no feed to watch, this is the only thing Waymark tells a traveler
 * unprompted. Each is raised once per booking and start time — moving a
 * booking raises a fresh one, because the time changed.
 */
data class RaisedReminder(
    val signature: String,
    val segmentId: String,
    /** What the booking is called — `BA286`, `The Bloomsbury Rooms`. */
    val label: String,
    val headline: String,
    val detail: String,
    val startEpochMillis: Long,
    val raisedAtMillis: Long,
    /** Shown by Android; false while notifications were blocked. */
    val posted: Boolean = false,
    /** Dismissed from the trip screen. */
    val acknowledged: Boolean = false,
)
