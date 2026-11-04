package xx.wallart.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import xx.wallart.CmRect
import xx.wallart.EditorState
import xx.wallart.Homography
import xx.wallart.Picture
import xx.wallart.Wall

/** How far the light from above and a little left throws a picture's shadow, in centimetres. */
private const val SHADOW_DX_CM = 0.8
private const val SHADOW_DY_CM = 1.6

/** How far the shadow's edge fades out, in centimetres, and in how many steps. */
private const val SHADOW_SPREAD_CM = 2.4
private const val SHADOW_LAYERS = 8

/** How dark the shadow is where every step lies over another. */
private const val SHADOW_ALPHA = 0.45f

/** What an opening with no artwork in it yet shows: a pale grey, like a blank mount. */
private val EMPTY_OPENING = Color(0xFFE0E0E0)

/** The thin dark line at the frame's outer edge and round its opening, which gives it a little depth. */
private const val EDGE_ALPHA = 0.45f

/**
 * Draws the pictures onto the wall's photo through the calibration's
 * projection: each frame and artwork in the wall's perspective — a photo
 * through [Matrix.setPolyToPoly], a colour as the projected outline — and
 * a soft shadow under each when the wall casts them. Its paints and paths
 * are kept between frames.
 */
internal class PicturePainter {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val perspective = Matrix()
    private val path = Path()

    /** The editor's pictures over its photo, [selected] ringed in [accent]. */
    fun draw(scope: DrawScope, state: EditorState, box: ImageBox, homography: Homography, selected: Long?, accent: Color) {
        val edge = with(scope) { 1.dp.toPx() }
        scope.drawIntoCanvas { composeCanvas ->
            paint(composeCanvas.nativeCanvas, state.wall, { state.photos[it]?.asAndroidBitmap() }, box, homography, edge) { id, outer ->
                if (id == selected) {
                    outline(composeCanvas.nativeCanvas, outer, Color.White, edge * 4)
                    outline(composeCanvas.nativeCanvas, outer, accent, edge * 2)
                }
            }
        }
    }

    /**
     * [wall]'s pictures onto [canvas], its photo lying in [box]; [photo] gives
     * each picture's photos by name, null for one not to hand. [edge] is the
     * width of the frame's thin edges; [after] is told each picture drawn, with
     * its outline, to draw over it.
     */
    fun paint(
        canvas: Canvas,
        wall: Wall,
        photo: (String) -> Bitmap?,
        box: ImageBox,
        homography: Homography,
        edge: Float,
        after: (Long, List<Offset>) -> Unit = { _, _ -> },
    ) {
        // Clipped to the photo: a picture hung past its edge is cut off there, as in the image saved.
        canvas.withClip(box.left, box.top, box.left + box.width, box.top + box.height) {
            for (picture in wall.pictures) {
                fun project(rect: CmRect): List<Offset>? = rect.corners.map { homography.map(it)?.let(box::toScreen) ?: return null }
                // A picture any corner of which is past the horizon cannot be drawn whole: it is not drawn.
                val outer = project(picture.bounds) ?: continue
                val opening = project(picture.opening) ?: continue
                if (wall.shadow) drawShadow(canvas, picture.bounds, ::project)
                drawFrame(canvas, picture, outer, picture.framePhoto?.let(photo))
                drawArt(canvas, picture, opening, picture.artPhoto?.let(photo))
                outline(canvas, outer, Color.Black.copy(alpha = EDGE_ALPHA), edge)
                outline(canvas, opening, Color.Black.copy(alpha = EDGE_ALPHA), edge)
                after(picture.id, outer)
            }
        }
    }

    /**
     * A shadow below and right of [bounds], in [SHADOW_LAYERS] growing steps
     * each faint, so it is darkest under the picture and fades at its edge:
     * a blur would be blurred before the projection, not after.
     */
    private fun drawShadow(canvas: Canvas, bounds: CmRect, project: (CmRect) -> List<Offset>?) {
        val shifted = CmRect(bounds.left + SHADOW_DX_CM, bounds.top + SHADOW_DY_CM, bounds.width, bounds.height)
        fillPaint.color = Color.Black.copy(alpha = SHADOW_ALPHA / SHADOW_LAYERS).toArgb()
        for (step in 1..SHADOW_LAYERS) {
            val grow = SHADOW_SPREAD_CM * step / SHADOW_LAYERS - SHADOW_SPREAD_CM / 2
            val quad = project(shifted.inset(-grow)) ?: continue
            canvas.drawPath(quadPath(quad), fillPaint)
        }
    }

    /** The frame: its photo stretched over it whole, or its colour. */
    private fun drawFrame(canvas: Canvas, picture: Picture, outer: List<Offset>, photo: Bitmap?) {
        if (photo != null) {
            drawBitmapInto(canvas, photo, RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()), outer)
        } else {
            fillPaint.color = picture.frameColor
            canvas.drawPath(quadPath(outer), fillPaint)
        }
    }

    /** The artwork filling the opening, cropped from the middle to its shape; a blank mount while there is none. */
    private fun drawArt(canvas: Canvas, picture: Picture, opening: List<Offset>, photo: Bitmap?) {
        if (photo == null) {
            fillPaint.color = EMPTY_OPENING.toArgb()
            canvas.drawPath(quadPath(opening), fillPaint)
            return
        }
        val shape = picture.opening.height / picture.opening.width
        val w = photo.width.toFloat()
        val h = photo.height.toFloat()
        val src = if (h / w > shape) {
            val cropped = (w * shape).toFloat()
            RectF(0f, (h - cropped) / 2, w, (h + cropped) / 2)
        } else {
            val cropped = (h / shape).toFloat()
            RectF((w - cropped) / 2, 0f, (w + cropped) / 2, h)
        }
        drawBitmapInto(canvas, photo, src, opening)
    }

    /** The [src] part of [bitmap] drawn onto the quadrilateral [dst] in perspective, and nothing of it outside. */
    private fun drawBitmapInto(canvas: Canvas, bitmap: Bitmap, src: RectF, dst: List<Offset>) {
        val from = floatArrayOf(src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom)
        val to = FloatArray(8).also { dst.forEachIndexed { i, o -> it[2 * i] = o.x; it[2 * i + 1] = o.y } }
        if (!perspective.setPolyToPoly(from, 0, to, 0, 4)) return
        canvas.withClip(quadPath(dst)) {
            concat(perspective)
            drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        }
    }

    private fun outline(canvas: Canvas, quad: List<Offset>, color: Color, width: Float) {
        strokePaint.color = color.toArgb()
        strokePaint.strokeWidth = width
        canvas.drawPath(quadPath(quad), strokePaint)
    }

    /** [quad] as the one path this painter keeps, refilled each time. */
    private fun quadPath(quad: List<Offset>): Path = path.apply {
        rewind()
        moveTo(quad[0].x, quad[0].y)
        for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
        close()
    }
}
