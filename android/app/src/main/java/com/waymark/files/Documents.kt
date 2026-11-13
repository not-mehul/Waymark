package com.waymark.files

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.core.content.FileProvider
import com.waymark.share.Exports
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files that stay on the phone: the weekly backup in Documents/Waymark/Backups,
 * and a ticket or document opened in whatever app reads it.
 */
object Documents {

    private const val BACKUP_DIR = "Waymark/Backups"

    /**
     * Writes [text] as Documents/Waymark/Backups/[name] and keeps the newest
     * [keep] backups there. Returns where it went, as a person would say it.
     *
     * Android 10 and later take it through MediaStore, which needs no storage
     * permission; earlier versions get the app's own Documents folder, which
     * is still reachable from a file manager over USB.
     */
    suspend fun writeBackup(context: Context, name: String, text: String, keep: Int): String =
        withContext(Dispatchers.IO) {
            val safe = Exports.sanitize(name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR/")
                }
                val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Could not create the backup")
                try {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                        ?: throw IllegalStateException("Could not write the backup")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                pruneMediaStore(context, keep)
                "Documents/$BACKUP_DIR/$safe"
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR).apply { mkdirs() }
                File(dir, safe).writeText(text)
                dir.listFiles { f -> f.name.startsWith("waymark-backup-") }
                    ?.sortedByDescending { it.name }
                    ?.drop(keep)
                    ?.forEach { it.delete() }
                "Android/data/${context.packageName}/files/Documents/$BACKUP_DIR/$safe"
            }
        }

    /** Only Waymark's own backups are listed (MediaStore shows an app its own files), newest name first. */
    private fun pruneMediaStore(context: Context, keep: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR/", "waymark-backup-%")
        val found = mutableListOf<Pair<Long, String>>()
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) found += cursor.getLong(0) to cursor.getString(1)
        }
        found.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    /** A ticket or document, written to cacheDir/exports and opened in the app that reads its type. */
    suspend fun open(activity: Activity, name: String, mime: String, bytes: ByteArray): Boolean {
        val file = withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
            File(dir, Exports.sanitize(name)).apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            activity.startActivity(view)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            false
        }
    }

    /** Full brightness and the screen kept on, while a pass is shown to a scanner; or back to normal. */
    fun bright(activity: Activity, on: Boolean) {
        val window = activity.window
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Whether an app answers [url] (a sibling's scheme, style.md §14.1). */
    fun canOpen(context: Context, url: String): Boolean =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).resolveActivity(context.packageManager) != null

    fun openUrl(activity: Activity, url: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }
}
