package com.waymark.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * Codes read from a picture with ZXing and nothing else: no camera
 * permission, no network, no Play services. A boarding pass from a screenshot
 * of a mobile one or a photo of a printed one, or a friend's trip code. The
 * page picks the picture and shrinks it before it crosses the bridge.
 */
object Barcodes {

    private const val MAX_SIDE = 2048

    /** The text of the first QR, Aztec or PDF417 code in [image], or null when none can be read. */
    fun read(image: ByteArray): String? {
        val bitmap = decodeScaled(image) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
                BarcodeFormat.DATA_MATRIX,
            ),
            DecodeHintType.TRY_HARDER to true,
        )
        // The hybrid binarizer suits screenshots; the global one copes better
        // with a photo of paper under uneven light. Try one, then the other.
        return listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source)).firstNotNullOfOrNull { binarizer ->
            try {
                MultiFormatReader().decode(BinaryBitmap(binarizer), hints).text
            } catch (_: NotFoundException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    /** The picture, shrunk to at most [MAX_SIDE] on its long side: a 48 MP photo would not fit in memory. */
    private fun decodeScaled(image: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(image, 0, image.size, options)
    }
}
