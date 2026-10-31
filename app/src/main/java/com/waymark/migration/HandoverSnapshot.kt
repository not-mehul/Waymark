package com.waymark.migration

import android.content.Context
import com.waymark.data.repo.TripRepository
import com.waymark.data.share.TripArchive
import java.io.File

/**
 * Keeps a full backup of every trip in the app's private storage, rewritten
 * after each change, for the move to the Svelte and Capacitor version of
 * Waymark. That version can't open this app's Room database, so on its first
 * launch it imports this file instead and deletes it once every trip is in.
 *
 * It is the same `waymark` archive that "Back up every trip" writes, so the
 * new app needs only the one reader for both. The file stays in the app's
 * private storage: it is never shared, and cloud backup is off for the whole
 * app, as it is for the database.
 */
object HandoverSnapshot {

    const val DIRECTORY = "handover"
    const val FILE_NAME = "waymark-archive.json"

    fun file(context: Context): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    /** Does file I/O: call from an I/O dispatcher. */
    suspend fun write(context: Context, trips: TripRepository) {
        val text = TripArchive.encode(TripArchive.archiveOf(trips.bundles()))
        val target = file(context)
        target.parentFile?.mkdirs()
        // Write beside it and swap in, so a crash mid-write never leaves a
        // truncated archive for the new app to read.
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not replace ${target.name}" }
        }
    }
}
