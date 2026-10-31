package com.waymark.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Whether Waymark may go online for its two extras — the weather where the
 * trip is going, and today's exchange rates. Off until the traveler turns it
 * on; with it off the app makes no network request at all.
 */
class OnlineStore(context: Context) {

    private val store = context.applicationContext.settings

    val enabled: Flow<Boolean> = store.safeData().map { it[ENABLED] == true }.distinctUntilChanged()

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun set(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("online.extras")
    }
}
