package xx.wallart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The photos the work is made of — the wall's, and each picture's artwork and
 * frame — kept as files of the app's: a photo picked from the gallery may not
 * be readable at the next start, a copy always is. Each is kept turned
 * upright and no larger than any image saved from it needs.
 */
object Photos {
    /** The wall's photo is the whole saved image: kept as long as the largest one saved. */
    private const val WALL_KEPT = 4096
    private const val WALL_SHOWN = 2048

    /** A picture's photos are a part of it, a few at a time on screen: half the wall's each way. */
    private const val PICTURE_KEPT = 2048
    private const val PICTURE_SHOWN = 1024

    private const val JPEG_QUALITY = 92
    private const val WALL_FILE = "wall.jpg"
    private const val PICTURE_DIR = "pictures"

    /** [uri] copied in as the wall's photo, and that photo as the editor shows it. Throws when it cannot be read or written. */
    fun importWall(context: Context, uri: Uri): Bitmap =
        import(context, uri, File(context.filesDir, WALL_FILE), WALL_KEPT, WALL_SHOWN)

    /**
     * [uri] copied in as a picture's photo, under a name of its own — the name
     * the picture keeps it by — and that photo as the editor shows it.
     * Throws when it cannot be read or written.
     */
    fun importPicturePhoto(context: Context, uri: Uri): Pair<String, Bitmap> {
        val name = "${UUID.randomUUID()}.jpg"
        val dir = File(context.filesDir, PICTURE_DIR).apply { mkdirs() }
        return name to import(context, uri, File(dir, name), PICTURE_KEPT, PICTURE_SHOWN)
    }

    private fun import(context: Context, uri: Uri, target: File, kept: Int, shown: Int): Bitmap {
        val bitmap = decode(ImageDecoder.createSource(context.contentResolver, uri), kept)
        // Written aside and moved over: a copy cut short never stands in for the photo.
        val partial = File(target.path + ".part")
        partial.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG not written" }
        }
        check(partial.renameTo(target)) { "photo not moved into place" }
        return fit(bitmap, shown)
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

    /** [bitmap] scaled down to [longSide] on its long side, or itself when it is no longer; a bitmap scaled is recycled. */
    private fun fit(bitmap: Bitmap, longSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= longSide) return bitmap
        val k = longSide.toDouble() / side
        val scaled = bitmap.scale((bitmap.width * k).roundToInt().coerceAtLeast(1), (bitmap.height * k).roundToInt().coerceAtLeast(1))
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
