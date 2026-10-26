package com.waymark.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.waymark.data.share.TripArchive
import com.waymark.domain.logic.IcsExport
import com.waymark.domain.logic.MarkdownExport
import com.waymark.domain.model.TripDossier
import com.waymark.ui.trip.TripUiState
import java.io.File

/**
 * Hands the trip to whatever the traveler wants to keep it in, in one of three
 * forms: a markdown itinerary to read, a calendar file whose events land in
 * any calendar app, and a Waymark trip file that another phone opens back into
 * a whole trip.
 *
 * Each file is written to the cache and shared through a `FileProvider`, so
 * the receiving app gets a read grant for one document rather than any
 * standing access. Nothing is uploaded by Waymark itself — where the file goes
 * next is entirely the traveler's choice, made in the system share sheet.
 */
object TripExport {

    private const val TAG = "WaymarkExport"

    /** The itinerary as markdown, with the text alongside for apps that take only text. */
    fun shareMarkdown(context: Context, state: TripUiState): Boolean {
        val dossier = state.dossier ?: return false
        val payload = MarkdownExport.Payload(dossier = dossier, ideas = state.ideas, analytics = state.analytics)
        val text = MarkdownExport.render(payload)
        return share(context, dossier, MarkdownExport.fileName(payload), text, "text/markdown", alsoAsText = true)
    }

    /** Every booking as a calendar event, in UTC, so no calendar can misplace it. */
    fun shareCalendar(context: Context, state: TripUiState): Boolean {
        val dossier = state.dossier ?: return false
        return share(context, dossier, IcsExport.fileName(dossier), IcsExport.render(dossier), "text/calendar")
    }

    /** The whole trip — party, bookings and ideas — for Waymark on another phone. */
    fun shareTripFile(context: Context, state: TripUiState): Boolean {
        val dossier = state.dossier ?: return false
        val bundle = TripArchive.TripBundle(
            trip = dossier.trip,
            travelers = dossier.party.travelers,
            segments = dossier.segments,
            ideas = state.ideas,
        )
        val text = TripArchive.encode(TripArchive.archiveOf(listOf(bundle)))
        val name = MarkdownExport.fileName(MarkdownExport.Payload(dossier)).removeSuffix(".md") + ".waymark.json"
        return share(context, dossier, name, text, TripArchive.MIME)
    }

    /**
     * Write [text] to the cache and open the share sheet. Returns false when
     * the file could not be written, so the caller can say so rather than
     * appearing to do nothing.
     */
    private fun share(
        context: Context,
        dossier: TripDossier,
        fileName: String,
        text: String,
        mime: String,
        alsoAsText: Boolean = false,
    ): Boolean {
        val uri = write(context, fileName, text) ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, dossier.trip.name)
            // Text alongside the stream, so apps that take only one or the
            // other — a note field, a message box — still receive the itinerary.
            if (alsoAsText) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share ${dossier.trip.name}").apply {
            // The chooser is launched from a context that may not be an
            // activity when this is called from a composable's callback.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return true
    }

    private fun write(context: Context, fileName: String, text: String): Uri? = runCatching {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        // One file per trip and form, overwritten each time: a folder of near
        // identical exports helps nobody.
        val file = File(directory, fileName)
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }.getOrElse { error ->
        Log.w(TAG, "Could not write $fileName", error)
        null
    }
}
