package com.waymark.ui.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.waymark.domain.logic.MarkdownExport
import com.waymark.ui.trip.TripUiState
import java.io.File

/**
 * Hands the trip to whatever the traveler wants to keep it in.
 *
 * The file is written to the cache and shared through a `FileProvider`, so the
 * receiving app gets a read grant for one document rather than any standing
 * access. Nothing is uploaded by Waymark itself — where the markdown goes next
 * is entirely the traveler's choice, made in the system share sheet.
 */
object TripExport {

    private const val TAG = "WaymarkExport"
    private const val MIME = "text/markdown"

    /** The export as a string, for a preview or a clipboard copy. */
    fun render(state: TripUiState): String? {
        val dossier = state.dossier ?: return null
        return MarkdownExport.render(payloadOf(state, dossier))
    }

    /**
     * Write the export and open the share sheet. Returns false when there is
     * nothing to export or the file could not be written, so the caller can
     * say so rather than appearing to do nothing.
     */
    fun share(context: Context, state: TripUiState): Boolean {
        val dossier = state.dossier ?: return false
        val payload = payloadOf(state, dossier)

        val uri = runCatching {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            // One file per trip, overwritten each time: a folder of near
            // identical exports helps nobody.
            val file = File(directory, MarkdownExport.fileName(payload))
            file.writeText(MarkdownExport.render(payload))
            FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        }.getOrElse { error ->
            Log.w(TAG, "Could not write the export", error)
            return false
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, dossier.trip.name)
            // Text alongside the stream, so apps that take only one or the
            // other — a note field, a message box — still receive the itinerary.
            putExtra(Intent.EXTRA_TEXT, MarkdownExport.render(payload))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, "Export ${dossier.trip.name}").apply {
            // The chooser is launched from a context that may not be an
            // activity when this is called from a composable's callback.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return true
    }

    private fun payloadOf(
        state: TripUiState,
        dossier: com.waymark.domain.model.TripDossier,
    ) = MarkdownExport.Payload(
        dossier = dossier,
        ideas = state.ideas,
        documents = state.documents.map { it.document },
        analytics = state.analytics,
    )
}
