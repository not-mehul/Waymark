package com.waymark.data.local

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.waymark.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The traveler's choice of Dusk or Dawn, kept across launches.
 *
 * On Android 12 and later the choice is also handed to the system, so the
 * launch splash — drawn before any of the app's own code runs — is already in
 * the right theme. Earlier, the splash stays up until the choice has been
 * read, so the first frame is never the other theme.
 */
class ThemeStore(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.settings

    val mode: Flow<ThemeMode> = store.safeData()
        .map { preferences -> ThemeMode.entries.firstOrNull { it.name == preferences[KEY] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    suspend fun set(mode: ThemeMode) {
        store.edit { it[KEY] = mode.name }
        applyToSystem(mode)
    }

    private fun applyToSystem(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        appContext.getSystemService<UiModeManager>()?.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.DUSK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.DAWN -> UiModeManager.MODE_NIGHT_NO
            }
        )
    }

    private companion object {
        val KEY = stringPreferencesKey("theme")
    }
}
