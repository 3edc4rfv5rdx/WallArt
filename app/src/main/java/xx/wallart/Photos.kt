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
 * frame — kept as files of the app's, each under a name of its own that the
 * work keeps it by: a photo picked from the gallery may not be readable at
 * the next start, a copy always is. A file is never written over, so the work
 * saved and the draft can name the same one. Each is kept turned upright and
 * no larger than any image saved from it needs.
 */
object Photos {
    /** The wall's photo is the whole saved image: kept as long as the largest one saved. */
    private const val WALL_KEPT = 4096
    private const val WALL_SHOWN = 2048

    /** A picture's photos are a part of it, a few at a time on screen: half the wall's each way. */
    private const val PICTURE_KEPT = 2048
    private const val PICTURE_SHOWN = 1024

    private const val JPEG_QUALITY = 92
    private const val DIR = "photos"

    /** Where the files kept before they were named lay; swept away with the unnamed. */
    private val LEGACY = listOf("wall.jpg", "pictures")

    private fun file(context: Context, name: String) = File(File(context.filesDir, DIR), name)

    /**
     * [uri] copied in — as the wall's photo when [wall], a picture's otherwise —
     * under a new name, and that photo as the editor shows it. Throws when it
     * cannot be read or written.
     */
    fun import(context: Context, uri: Uri, wall: Boolean): Pair<String, Bitmap> {
        val bitmap = decode(ImageDecoder.createSource(context.contentResolver, uri), if (wall) WALL_KEPT else PICTURE_KEPT)
        val name = "${UUID.randomUUID()}.jpg"
        val target = file(context, name).also { it.parentFile?.mkdirs() }
        // Written aside and moved over: a copy cut short never stands in for the photo.
        val partial = File(target.path + ".part")
        partial.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG not written" }
        }
        check(partial.renameTo(target)) { "photo not moved into place" }
        return name to fit(bitmap, if (wall) WALL_SHOWN else PICTURE_SHOWN)
    }

    /** The photo kept as [name], as the editor shows it — the wall's when [wall]; null when it is gone or unreadable. */
    fun loadShown(context: Context, name: String, wall: Boolean): Bitmap? {
        val source = file(context, name).takeIf { it.exists() } ?: return null
        // Throwable, not Exception: a photo too large for memory is just as unreadable.
        return runCatching { decode(ImageDecoder.createSource(source), if (wall) WALL_SHOWN else PICTURE_SHOWN) }.getOrNull()
    }

    /** Every photo kept but not among [keep] deleted, and the files of before photos were named with them. */
    fun sweep(context: Context, keep: Set<String>) {
        File(context.filesDir, DIR).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        LEGACY.forEach { File(context.filesDir, it).deleteRecursively() }
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
