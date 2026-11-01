package com.waymark

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * The move from the Kotlin app. Its Room database cannot be opened from a web
 * page, so its last release keeps a full backup in private storage, rewritten
 * after every change (HandoverSnapshot.kt in app/). This app installs over it
 * with the same id and key, so the file is still here on its first launch: the
 * page reads it, imports every trip, checks they all read back, and only then
 * asks for it to be removed (src/share/handover.ts).
 */
object Handover {

    // HandoverSnapshot.DIRECTORY and FILE_NAME.
    private const val DIRECTORY = "handover"
    private const val FILE_NAME = "waymark-archive.json"

    /** WaymarkDatabase.NAME: there only on a phone the Kotlin app ran on. */
    private const val LEGACY_DATABASE = "waymark.db"

    private const val PREFERENCES = "waymark.handover"
    private const val DONE = "done"

    fun file(context: Context): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    /** The archive's text, or null when there is none. */
    fun read(context: Context): String? = file(context).takeIf { it.isFile }?.readText()

    fun remove(context: Context) {
        file(context).delete()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putBoolean(DONE, true) }
    }

    /**
     * The Kotlin app's database is here but its file is not: the release that
     * writes the file was skipped, and the trips can only come across from a
     * backup. Never true once a handover has happened.
     */
    fun stranded(context: Context): Boolean =
        !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DONE, false) &&
            !file(context).exists() &&
            context.getDatabasePath(LEGACY_DATABASE).exists()
}
