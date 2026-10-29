package com.waymark.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * The person's settings — reminder lead times, Dusk or Dawn, whether the
 * notification question has been asked — in one Preferences DataStore.
 *
 * DataStore rather than `SharedPreferences`: reads and writes are off the main
 * thread and transactional, and a change arrives as a flow rather than through
 * a listener that has to be registered and remembered. The three files earlier
 * builds wrote are folded in on first read, keys unchanged, and then deleted.
 */
internal val Context.settings: DataStore<Preferences> by preferencesDataStore(
    name = "waymark_settings",
    produceMigrations = { context ->
        listOf("waymark.reminders", "waymark.appearance", "waymark.permissions")
            .map { SharedPreferencesMigration(context, it) }
    },
)

/** The settings, with an unreadable file read as defaults rather than a crash. */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

/**
 * Android asks for the notification permission at most twice before it stops
 * showing the dialog, so the app asks on its own initiative once — the first
 * time a reminder would mean something — and after that only when asked to.
 */
object NotificationAsk {

    private val ASKED = booleanPreferencesKey("notifications.asked")

    /** True the first time it is called, ever; false after. */
    suspend fun firstTime(context: Context): Boolean {
        val store = context.applicationContext.settings
        if (store.safeData().first()[ASKED] == true) return false
        store.edit { it[ASKED] = true }
        return true
    }
}
