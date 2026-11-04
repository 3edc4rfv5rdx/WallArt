package xx.wallart.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import xx.wallart.CalibrationEdit
import xx.wallart.Pt
import xx.wallart.calibrationHomography
import xx.wallart.gridStep
import xx.wallart.parseCm
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** How near a finger has to come down to a corner to take it. */
private val CORNER_REACH = 40.dp
private val CORNER_RADIUS = 9.dp
private val LOUPE_RADIUS = 64.dp
private val LOUPE_MARGIN = 8.dp
private const val LOUPE_ZOOM = 3f

/** The size the grid is drawn for while the size fields hold none yet: just enough lines to show the perspective. */
private const val GRID_PARTS_WITHOUT_SIZE = 4.0

/**
 * The wall's photo fitted into the box and, while [calibrating], the
 * rectangle being placed on it: its corners dragged by a finger anywhere
 * near them, a grid across it that shows whether the perspective is right,
 * and a loupe over the corner being dragged, which the finger hides.
 */
@Composable
fun WallCanvas(
    image: ImageBitmap,
    calibrating: CalibrationEdit?,
    onMoveCorner: (Int, Pt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    var dragged by remember { mutableIntStateOf(-1) }
    val edit by rememberUpdatedState(calibrating)
    val move by rememberUpdatedState(onMoveCorner)

    Canvas(
        modifier.pointerInput(calibrating != null, image) {
            if (calibrating == null) return@pointerInput
            // Where the corner is meant to be, not where the finger is: the finger starts off the corner, and the
            // corner is moved by as much as the finger is, so it never jumps under it.
            var at = Pt(0.0, 0.0)
            detectDragGestures(
                onDragStart = start@{ down ->
                    val corners = edit?.corners ?: return@start
                    val box = fitImage(image, size.toSize())
                    val reach = CORNER_REACH.toPx()
                    val nearest = corners.indices.minBy { (box.toScreen(corners[it]) - down).getDistance() }
                    if ((box.toScreen(corners[nearest]) - down).getDistance() <= reach) {
                        dragged = nearest
                        at = corners[nearest]
                    }
                },
                onDragEnd = { dragged = -1 },
                onDragCancel = { dragged = -1 },
                onDrag = drag@{ change, delta ->
                    if (dragged < 0) return@drag
                    change.consume()
                    val box = fitImage(image, size.toSize())
                    at = Pt(at.x + delta.x / box.width, at.y + delta.y / box.height)
                    move(dragged, at)
                },
            )
        },
    ) {
        val box = fitImage(image, size)
        drawPhoto(image, box)
        val current = calibrating ?: return@Canvas
        val corners = current.corners.map(box::toScreen)
        // The grid needs a shape only: until both sides are typed in, it is drawn as a square's.
        val width = parseCm(current.widthText)
        val height = parseCm(current.heightText)
        val gridWidth = if (width != null && height != null) width else GRID_PARTS_WITHOUT_SIZE
        val gridHeight = if (width != null && height != null) height else GRID_PARTS_WITHOUT_SIZE
        val homography = calibrationHomography(current.corners, gridWidth, gridHeight)
        val color = if (homography == null) error else accent
        if (homography != null) drawGrid(gridWidth, gridHeight, box) { homography.map(it) }
        drawQuad(corners, color)
        corners.forEach { drawCorner(it, color) }
        if (dragged >= 0) drawLoupe(image, box, corners, dragged, color)
    }
}

/** Where the photo lies in the box: fitted whole, centred. */
private class ImageBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toScreen(p: Pt) = Offset(left + (p.x * width).toFloat(), top + (p.y * height).toFloat())
}

private fun fitImage(image: ImageBitmap, size: Size): ImageBox {
    val scale = min(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    return ImageBox((size.width - w) / 2, (size.height - h) / 2, w, h)
}

private fun DrawScope.drawPhoto(image: ImageBitmap, box: ImageBox) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
        dstSize = IntSize(box.width.roundToInt(), box.height.roundToInt()),
    )
}

/** Lines across the [widthCm] × [heightCm] rectangle at a round step, through [project] onto the photo. */
private fun DrawScope.drawGrid(widthCm: Double, heightCm: Double, box: ImageBox, project: (Pt) -> Pt?) {
    val step = gridStep(maxOf(widthCm, heightCm))
    val stroke = 1.dp.toPx()
    fun line(a: Pt, b: Pt) {
        val from = project(a) ?: return
        val to = project(b) ?: return
        // A dark line under a light one, so it shows on a pale wall and a dark one alike.
        drawLine(Color.Black.copy(alpha = 0.4f), box.toScreen(from), box.toScreen(to), stroke * 2)
        drawLine(Color.White.copy(alpha = 0.8f), box.toScreen(from), box.toScreen(to), stroke)
    }
    for (k in 1..floor(widthCm / step).toInt()) {
        val x = k * step
        if (x < widthCm) line(Pt(x, 0.0), Pt(x, heightCm))
    }
    for (k in 1..floor(heightCm / step).toInt()) {
        val y = k * step
        if (y < heightCm) line(Pt(0.0, y), Pt(widthCm, y))
    }
}

private fun DrawScope.drawQuad(corners: List<Offset>, color: Color) {
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.5f), style = Stroke(4.dp.toPx()))
    drawPath(path, color, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawCorner(at: Offset, color: Color) {
    drawCircle(Color.White, CORNER_RADIUS.toPx(), at)
    drawCircle(color, CORNER_RADIUS.toPx() - 2.dp.toPx(), at)
}

/**
 * The photo round corner [index] enlarged in a circle at the top of the box —
 * on the side away from the corner, so the finger never covers it — with a
 * cross on the corner's point.
 */
private fun DrawScope.drawLoupe(image: ImageBitmap, box: ImageBox, corners: List<Offset>, index: Int, color: Color) {
    val corner = corners[index]
    val radius = LOUPE_RADIUS.toPx()
    val margin = LOUPE_MARGIN.toPx()
    val onLeft = corner.x > size.width / 2
    val center = Offset(if (onLeft) margin + radius else size.width - margin - radius, margin + radius)
    val circle = Path().apply { addOval(Rect(center, radius)) }
    clipPath(circle) {
        drawRect(Color.Black)
        // The whole photo drawn LOUPE_ZOOM times larger, moved so the corner lies on the loupe's centre.
        val left = center.x - (corner.x - box.left) * LOUPE_ZOOM
        val top = center.y - (corner.y - box.top) * LOUPE_ZOOM
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize((box.width * LOUPE_ZOOM).roundToInt(), (box.height * LOUPE_ZOOM).roundToInt()),
        )
        // The rectangle's sides as they run from this corner, enlarged the same.
        fun zoomed(p: Offset) = center + (p - corner) * LOUPE_ZOOM
        val stroke = 2.dp.toPx()
        drawLine(color, center, zoomed(corners[(index + 1) % 4]), stroke)
        drawLine(color, center, zoomed(corners[(index + 3) % 4]), stroke)
    }
    drawCircle(Color.White, radius, center, style = Stroke(3.dp.toPx()))
    val arm = 10.dp.toPx()
    drawLine(Color.White, center - Offset(arm, 0f), center + Offset(arm, 0f), 2.dp.toPx())
    drawLine(Color.White, center - Offset(0f, arm), center + Offset(0f, arm), 2.dp.toPx())
}
