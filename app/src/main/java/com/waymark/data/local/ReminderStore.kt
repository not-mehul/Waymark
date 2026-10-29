package com.waymark.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.waymark.domain.logic.Lead
import com.waymark.domain.logic.ReminderCategory
import com.waymark.domain.logic.ReminderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where the reminder lead times live.
 *
 * In the settings DataStore rather than a Room table: a handful of integers
 * that belong to the person rather than to any trip, read by a background
 * check that has no view model to ask, and a table would mean a migration
 * every time the set of categories changed. A category never set reads as
 * its default, so a new category needs no migration either.
 */
class ReminderStore(context: Context) {

    private val store = context.applicationContext.settings

    fun observe(): Flow<ReminderPreferences> = store.safeData().map(::read).distinctUntilChanged()

    suspend fun current(): ReminderPreferences = observe().first()

    suspend fun set(category: ReminderCategory, lead: Lead) {
        store.edit { it[key(category)] = lead.minutes }
    }

    private fun read(preferences: Preferences): ReminderPreferences =
        ReminderCategory.entries.fold(ReminderPreferences.DEFAULT) { result, category ->
            preferences[key(category)]?.let { result.with(category, Lead(it)) } ?: result
        }

    private fun key(category: ReminderCategory): Preferences.Key<Int> =
        intPreferencesKey("lead.${category.name.lowercase()}")
}
