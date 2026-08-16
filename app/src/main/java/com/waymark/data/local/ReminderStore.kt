package com.waymark.data.local

import android.content.Context
import android.content.SharedPreferences
import com.waymark.domain.logic.Lead
import com.waymark.domain.logic.ReminderCategory
import com.waymark.domain.logic.ReminderPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Where the reminder lead times live.
 *
 * `SharedPreferences` rather than a Room table: this is four integers that
 * belong to the person rather than to any trip, they are read by a background
 * worker that has no view model to ask, and giving them a table would mean a
 * migration every time the set of categories changed.
 *
 * The flow is a `callbackFlow` over the change listener so the settings sheet
 * reflects an edit immediately, including one made while it is open.
 */
class ReminderStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun current(): ReminderPreferences {
        var result = ReminderPreferences.DEFAULT
        ReminderCategory.entries.forEach { category ->
            val stored = preferences.getInt(key(category), UNSET)
            if (stored != UNSET) result = result.with(category, Lead(stored))
        }
        return result
    }

    fun observe(): Flow<ReminderPreferences> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun set(category: ReminderCategory, lead: Lead) {
        preferences.edit().putInt(key(category), lead.minutes).apply()
    }

    private fun key(category: ReminderCategory): String = "lead.${category.name.lowercase()}"

    private companion object {
        const val NAME = "waymark.reminders"

        /** Distinct from `Lead.OFF`, which is a choice rather than a silence. */
        const val UNSET = -1
    }
}
