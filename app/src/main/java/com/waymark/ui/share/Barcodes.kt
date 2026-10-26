package com.waymark.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Codes in and out, with ZXing and nothing else: no camera permission, no
 * network, no Play Services. A trip's code is drawn here; a boarding pass or a
 * friend's trip code is read from a picture the traveler picks — a screenshot
 * of a mobile boarding pass, or a photo of a printed one.
 */
object Barcodes {

    /** A QR code for [text], [size] pixels square, dark on light. */
    suspend fun qr(text: String, size: Int): ImageBitmap = withContext(Dispatchers.Default) {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                // The lowest correction holds the most: a trip code is read off
                // a screen, not a crumpled sticker.
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 2,
            ),
        )
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix.get(index % matrix.width, index / matrix.width)) DARK else LIGHT
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /**
     * The text of the first QR, Aztec or PDF417 code in the picture at [uri],
     * or null when there is none that can be read.
     */
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(context, uri) ?: return@withContext null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
            ),
            DecodeHintType.TRY_HARDER to true,
        )
        // The hybrid binarizer suits screenshots; the global one copes better
        // with a photo of paper under uneven light. Try one, then the other.
        listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source)).firstNotNullOfOrNull { binarizer ->
            try {
                MultiFormatReader().decode(BinaryBitmap(binarizer), hints).text
            } catch (_: NotFoundException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    /** The picture, shrunk to at most [MAX_SIDE] on its long side — a 48MP photo would not fit in memory. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching null
        var sample = 1
        while (longest / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private const val MAX_SIDE = 2048
    private const val DARK = 0xFF1A1A1A.toInt()
    private const val LIGHT = 0xFFFFFFFF.toInt()
}
