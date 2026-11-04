package xx.wallart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The wall's photo, kept as a file of the app's: a picture picked from the
 * gallery may not be readable at the next start, a copy always is. Kept no
 * larger than the largest image saved from it, turned upright.
 */
object WallPhoto {
    /** The long side of the copy: the largest saved image's. */
    private const val KEPT_LONG_SIDE = 4096

    /** The long side the editor shows it at: sharp on a phone's screen, a quarter of the copy's memory. */
    private const val SHOWN_LONG_SIDE = 2048

    private const val JPEG_QUALITY = 92
    private const val FILE_NAME = "wall.jpg"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** [uri] copied in as the wall's photo, and that photo as the editor shows it. Throws when it cannot be read or written. */
    fun import(context: Context, uri: Uri): Bitmap {
        val kept = decode(ImageDecoder.createSource(context.contentResolver, uri), KEPT_LONG_SIDE)
        val target = file(context)
        // Written aside and moved over: a copy cut short never stands in for the photo.
        val partial = File(target.path + ".part")
        partial.outputStream().use { out ->
            check(kept.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG not written" }
        }
        check(partial.renameTo(target)) { "photo not moved into place" }
        return fit(kept, SHOWN_LONG_SIDE)
    }

    /**
     * [source] upright, no longer than [longSide] on its long side. Decoded at a
     * sample that leaves it at least that long, then scaled down the rest of the
     * way: a sample alone lands only on whole fractions of the size.
     */
    private fun decode(source: ImageDecoder.Source, longSide: Int): Bitmap {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software, so it can be scaled and compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > longSide) decoder.setTargetSampleSize(side / longSide)
        }
        return fit(bitmap, longSide)
    }

    /** [bitmap] scaled down to [longSide] on its long side, or itself when it is no longer. */
    private fun fit(bitmap: Bitmap, longSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= longSide) return bitmap
        val k = longSide.toDouble() / side
        val scaled = bitmap.scale((bitmap.width * k).roundToInt().coerceAtLeast(1), (bitmap.height * k).roundToInt().coerceAtLeast(1))
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
