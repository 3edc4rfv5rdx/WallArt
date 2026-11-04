package xx.wallart

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import xx.wallart.ui.ImageBox
import xx.wallart.ui.PicturePainter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** The long side an image is shared at, at most: plenty for a message, and quick to send. */
const val SHARE_LONG_SIDE = 2048

/** The folder under Pictures the images are saved into. */
const val SAVE_FOLDER = "WallArt"

/** A picture's photo is read a little larger than it is drawn, so the projection's scaling never blurs it. */
private const val PHOTO_MARGIN = 1.25

/** The width of the frames' thin edges, as a part of the image's long side. */
private const val EDGE_PER_LONG_SIDE = 1f / 1000

private const val JPEG_QUALITY = 92

object Export {

    /**
     * [wall] as its format saves it — its crop of the photo, with the pictures
     * on it through the calibration — [longSide] long on the long side, or as
     * long as the format says when that is shorter. Throws when the photo
     * cannot be read or there is no memory for it.
     */
    fun render(context: Context, wall: Wall, longSide: Int = wall.format.longSide): Bitmap {
        val name = checkNotNull(wall.photo) { "no photo" }
        val (photoW, photoH) = checkNotNull(Photos.keptSize(context, name)) { "photo unreadable" }
        val photoRatio = photoW.toDouble() / photoH
        val (outW, outH) = wall.format.savedSize(photoRatio, minOf(longSide, wall.format.longSide))
        val crop = wall.format.crop(photoRatio)
        // The whole photo as it lies under the image: the crop fills the image, the rest falls off its edges.
        val box = ImageBox(
            left = (-crop.left / crop.width * outW).toFloat(),
            top = (-crop.top / crop.height * outH).toFloat(),
            width = (outW / crop.width).toFloat(),
            height = (outH / crop.height).toFloat(),
        )
        // Read only as large as it is drawn — never larger than it is kept.
        val photo = checkNotNull(Photos.loadKept(context, name, ceil(max(box.width, box.height).toDouble()).toInt())) {
            "photo unreadable"
        }
        val image = try {
            createBitmap(outW, outH)
        } catch (e: Throwable) {
            photo.recycle()
            throw e
        }
        val canvas = Canvas(image)
        canvas.drawBitmap(photo, null, RectF(box.left, box.top, box.left + box.width, box.top + box.height), Paint(Paint.FILTER_BITMAP_FLAG))
        photo.recycle()
        val homography = wall.calibration?.wallToPhoto() ?: return image
        val photos = picturePhotos(context, wall, box, homography)
        try {
            val edge = max(outW, outH) * EDGE_PER_LONG_SIDE
            PicturePainter().paint(canvas, wall, { photos[it] }, box, homography, edge)
        } finally {
            photos.values.forEach(Bitmap::recycle)
        }
        return image
    }

    /**
     * The pictures' photos, each read at the size its largest picture is drawn
     * at in [box] — not the size it is kept at, which for a small picture on a
     * large image is memory spent on nothing. One that cannot be read is left
     * out, and drawn as missing.
     */
    private fun picturePhotos(context: Context, wall: Wall, box: ImageBox, homography: Homography): Map<String, Bitmap> {
        val needed = mutableMapOf<String, Int>()
        for (picture in wall.pictures) {
            val corners = picture.bounds.corners.mapNotNull { homography.map(it)?.let(box::toScreen) }
            // A corner past the horizon: the picture is not drawn, and needs nothing read.
            if (corners.size < 4) continue
            val drawn = corners.indices.maxOf { i ->
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            }
            val side = ceil(drawn * PHOTO_MARGIN).toInt().coerceAtLeast(1)
            listOfNotNull(picture.artPhoto, picture.framePhoto).forEach { needed[it] = max(needed[it] ?: 0, side) }
        }
        return needed.mapNotNull { (name, side) -> Photos.loadKept(context, name, side)?.let { name to it } }.toMap()
    }

    /** [bitmap] saved as a JPEG into Pictures/[SAVE_FOLDER] through the media library, which needs no permission. Throws on failure. */
    fun saveToPictures(context: Context, bitmap: Bitmap) {
        val resolver = context.contentResolver
        val name = "WallArt_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SAVE_FOLDER")
            // Hidden from the gallery until it is whole.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)) {
            "not inserted"
        }
        try {
            checkNotNull(resolver.openOutputStream(uri)) { "not opened" }.use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG not written" }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // A half-written entry would stay in the gallery as a broken picture.
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** [bitmap] as a JPEG in the app's cache, by a URI another app can be given to read it. Throws on failure. */
    fun shareFile(context: Context, bitmap: Bitmap): Uri {
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "wallart.jpg")
        file.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG not written" }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
