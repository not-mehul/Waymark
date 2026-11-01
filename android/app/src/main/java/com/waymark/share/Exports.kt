package com.waymark.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import com.waymark.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files out of Waymark, to wherever the traveler wants them: the system share
 * sheet (where Files, Drive and every mail app are choices), and Android's
 * print dialog, which also saves a PDF.
 */
object Exports {

    /** The page's text, as a file in cacheDir/exports, handed to the share sheet. */
    suspend fun share(activity: Activity, name: String, text: String, mime: String, title: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
            File(dir, sanitize(name)).apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_chooser_title)))
    }

    /**
     * The page as it is laid out for print (its `@media print` rules) through
     * Android's print dialog, on A4. [onFinish] runs once the dialog has
     * closed and the job is spooled, so the page can take its print layout
     * down only then.
     */
    fun print(activity: Activity, webView: WebView, name: String, onFinish: () -> Unit) {
        val manager = activity.getSystemService<PrintManager>()
        if (manager == null) {
            onFinish()
            return
        }
        val adapter = Finishing(webView.createPrintDocumentAdapter(name), onFinish)
        val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
        manager.print(name, adapter, attributes)
    }

    // Keep the name inside cacheDir/exports: no separators, and no leading
    // dots, so "." and ".." can never resolve to a directory.
    internal fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "waymark.txt" }

    /** The WebView's adapter, telling us when the print dialog is done with it. */
    private class Finishing(
        private val inner: PrintDocumentAdapter,
        private val onDone: () -> Unit,
    ) : PrintDocumentAdapter() {
        override fun onStart() = inner.onStart()

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) = inner.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) = inner.onWrite(pages, destination, cancellationSignal, callback)

        override fun onFinish() {
            inner.onFinish()
            onDone()
        }
    }
}
