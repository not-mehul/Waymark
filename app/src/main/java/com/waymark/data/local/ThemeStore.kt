package com.waymark.data.local

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.waymark.ui.theme.ThemeMode

/**
 * The traveler's choice of Dusk or Dawn, kept across launches.
 *
 * It used to live in `rememberSaveable`, which survives a rotation and nothing
 * else: every cold start went back to following the system. On Android 12 and
 * later the choice is also handed to the system, so the launch splash — drawn
 * before any of the app's own code runs — is already in the right theme.
 */
class ThemeStore(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun current(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY, null) } ?: ThemeMode.SYSTEM

    fun set(mode: ThemeMode) {
        preferences.edit().putString(KEY, mode.name).apply()
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
        const val NAME = "waymark.appearance"
        const val KEY = "theme"
    }
}
