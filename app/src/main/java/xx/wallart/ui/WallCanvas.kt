package xx.wallart.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import xx.wallart.Aspect
import xx.wallart.CalibrationEdit
import xx.wallart.CmRect
import xx.wallart.Crop
import xx.wallart.EditorState
import xx.wallart.Guide
import xx.wallart.Picture
import xx.wallart.Homography
import xx.wallart.Pt
import xx.wallart.R
import xx.wallart.calibrationHomography
import xx.wallart.formatCm
import xx.wallart.gridStep
import xx.wallart.insideConvex
import xx.wallart.parseCm
import xx.wallart.snapPicture
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** How far above the selected picture's top its size stands: clear of the handles there. */
private val LABEL_LIFT = 14.dp

/** How dark what the crop leaves out is drawn. */
private const val CROP_DIM = 0.55f

/** How near on screen a dragged picture's edge, centre or gap has to come to another's to snap to it. */
private val SNAP_REACH = 10.dp

/** How near a finger has to come down to a corner to take it. */
private val CORNER_REACH = 40.dp
private val CORNER_RADIUS = 9.dp
private val LOUPE_RADIUS = 64.dp
private val LOUPE_MARGIN = 8.dp
private const val LOUPE_ZOOM = 3f

/** How far two fingers may enlarge the photo to look closer. */
private const val MAX_ZOOM = 8f

/** The size the grid is drawn for while the size fields hold none yet: just enough lines to show the perspective. */
private const val GRID_PARTS_WITHOUT_SIZE = 4.0

/**
 * The wall's photo fitted into the box with the pictures on it, and either
 * the calibration under way or the pictures to tap and drag.
 *
 * While calibrating: the rectangle being placed, its corners dragged by a
 * finger anywhere near them, a grid across it that shows whether the
 * perspective is right, a loupe over the corner being dragged, which the
 * finger hides, and the pictures as the calibration would place them.
 *
 * Otherwise: a tap selects the picture under it, or none; a drag moves the
 * picture it starts on across the wall, in the wall's centimetres, so it
 * shrinks as it goes further off.
 *
 * Either way two fingers enlarge and move the view, for a closer look, and
 * while it is enlarged one finger moves it too off the pictures and corners;
 * the saved image is not touched by it. A new photo starts at the whole of it.
 */
@Composable
fun WallCanvas(
    state: EditorState,
    image: ImageBitmap,
    onMoveCorner: (Int, Pt) -> Unit,
    onSelect: (Long?) -> Unit,
    onMovePicture: (Long, Double, Double) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onMoveCrop: (Double, Double, Double) -> Unit,
    onResizePicture: (Long, Picture, Int, Pt) -> Unit,
    sizing: Boolean,
    modifier: Modifier = Modifier,
) {
    val cm = stringResource(R.string.cm)
    // The guides of the snap the picture being dragged is held at; none between drags.
    var guides by remember { mutableStateOf(emptyList<Guide>()) }
    val guidePainter = remember { GuidePainter() }
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val calibrating = state.calibrating
    var dragged by remember { mutableIntStateOf(-1) }
    val current by rememberUpdatedState(state)
    val moveCorner by rememberUpdatedState(onMoveCorner)
    val select by rememberUpdatedState(onSelect)
    val movePicture by rememberUpdatedState(onMovePicture)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val moveCrop by rememberUpdatedState(onMoveCrop)
    val resizePicture by rememberUpdatedState(onResizePicture)
    val isSizing by rememberUpdatedState(sizing)
    val painter = remember { PicturePainter() }
    var view by remember(image) { mutableStateOf(Zoom()) }
    val zoom = { view }
    val pan: PointerInputScope.(Offset) -> Unit = { delta ->
        view = view.pinched(viewBox(image, size.toSize(), view), size.toSize(), Offset.Zero, delta, 1f)
    }

    val pinch = Modifier.pointerInput(image) {
        pinch { centroid, pan, factor -> view = view.pinched(viewBox(image, size.toSize(), view), size.toSize(), centroid, pan, factor) }
    }
    val gestures = if (calibrating != null) {
        Modifier.pointerInput(image) {
            dragCorners(image, zoom, { pan(it) }, { current.calibrating?.corners }, { dragged = it }) { index, to -> moveCorner(index, to) }
        }
    } else {
        Modifier
            .pointerInput(image) {
                detectTapGestures { down -> select(pictureAt(current, viewBox(image, size.toSize(), zoom()), down)) }
            }
            .pointerInput(image) {
                dragPictures(
                    image,
                    zoom,
                    onPan = { pan(it) },
                    { current },
                    onSelect = select,
                    onGuides = { guides = it },
                    onBegin = { dragStart() },
                    onEnd = { dragEnd() },
                    onMoveCrop = { dx, dy -> moveCrop(dx, dy, image.width.toDouble() / image.height) },
                    onResize = { id, from, handle, to -> resizePicture(id, from, handle, to) },
                    sizing = { isSizing },
                ) { id, left, top -> movePicture(id, left, top) }
            }
    }

    // The calibration's corners kept from the system's back gesture: one put near the screen's edge is still dragged.
    val host = LocalView.current
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val reach = with(LocalDensity.current) { CORNER_REACH.toPx() }
    val zoomNow = view
    fun exclude() {
        val coordinates = placed[0]?.takeIf { it.isAttached }
        val corners = current.calibrating?.corners
        host.systemGestureExclusionRects = if (coordinates == null || corners == null) {
            emptyList()
        } else {
            val box = viewBox(image, coordinates.size.toSize(), zoomNow)
            corners.map { corner ->
                val at = coordinates.localToRoot(box.toScreen(corner))
                android.graphics.Rect((at.x - reach).toInt(), (at.y - reach).toInt(), (at.x + reach).toInt(), (at.y + reach).toInt())
            }
        }
    }
    SideEffect { exclude() }
    DisposableEffect(host) { onDispose { host.systemGestureExclusionRects = emptyList() } }

    Canvas(
        modifier
            .clipToBounds()
            .onGloballyPositioned {
                placed[0] = it
                exclude()
            }
            .then(pinch)
            .then(gestures),
    ) {
        val box = viewBox(image, size, view)
        drawPhoto(image, box)
        // While calibrating, the pictures move with the corners: the calibration as it would be, once it is one.
        val homography = if (calibrating != null) {
            calibrating.result?.projection(state.wall.straight, box.ratio)
        } else {
            state.wall.projection(box.ratio)
        }
        if (homography != null) {
            val selected = if (calibrating == null) state.selected else null
            painter.draw(this, state, box, homography, selected, accent)
            if (calibrating == null) {
                guidePainter.draw(this, guides, box, homography, accent, cm)
                // The handles and the size only while sizing: a picture merely picked is not being measured.
                state.selectedPicture?.takeIf { sizing }?.let { picture ->
                    val handles = picture.bounds.handles.mapNotNull { homography.map(it)?.let(box::toScreen) }
                    if (handles.size == HANDLE_COUNT) {
                        handles.forEach { drawCorner(it, accent) }
                        // The artwork's size, which is what is bought, over the top edge — clear of its handles.
                        val top = Offset(handles[4].x, minOf(handles[0].y, handles[1].y, handles[4].y) - LABEL_LIFT.toPx())
                        val o = picture.opening
                        guidePainter.label(this, "${formatCm(o.width)}×${formatCm(o.height)} $cm", top, accent, above = true)
                    }
                }
            }
        }
        if (calibrating == null && state.wall.format.aspect != Aspect.ORIGINAL) {
            drawCrop(state.wall.format.crop(image.width.toDouble() / image.height), box)
        }
        if (calibrating != null) drawCalibration(calibrating, image, box, dragged, accent, error)
    }
}

/** What falls outside the crop dimmed, so the image saved is what stays bright, with a line round it. */
private fun DrawScope.drawCrop(crop: Crop, box: ImageBox) {
    val topLeft = box.toScreen(Pt(crop.left, crop.top))
    val bottomRight = box.toScreen(Pt(crop.left + crop.width, crop.top + crop.height))
    val dim = Color.Black.copy(alpha = CROP_DIM)
    val left = box.left
    val right = box.left + box.width
    val top = box.top
    val bottom = box.top + box.height
    // Above, below, and either side between them.
    drawRect(dim, Offset(left, top), Size(box.width, topLeft.y - top))
    drawRect(dim, Offset(left, bottomRight.y), Size(box.width, bottom - bottomRight.y))
    drawRect(dim, Offset(left, topLeft.y), Size(topLeft.x - left, bottomRight.y - topLeft.y))
    drawRect(dim, Offset(bottomRight.x, topLeft.y), Size(right - bottomRight.x, bottomRight.y - topLeft.y))
    drawRect(Color.White, topLeft, Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y), style = Stroke(1.5.dp.toPx()))
}

/** The calibration under way over the photo: grid, rectangle, corners, and the loupe while a corner is dragged. */
private fun DrawScope.drawCalibration(
    edit: CalibrationEdit,
    image: ImageBitmap,
    box: ImageBox,
    dragged: Int,
    accent: Color,
    error: Color,
) {
    val corners = edit.corners.map(box::toScreen)
    // The grid needs a shape only: until both sides are typed in, it is drawn as a square's.
    val width = parseCm(edit.widthText)
    val height = parseCm(edit.heightText)
    val gridWidth = if (width != null && height != null) width else GRID_PARTS_WITHOUT_SIZE
    val gridHeight = if (width != null && height != null) height else GRID_PARTS_WITHOUT_SIZE
    val homography = calibrationHomography(edit.corners, gridWidth, gridHeight)
    val color = if (homography == null) error else accent
    if (homography != null) drawGrid(gridWidth, gridHeight, box) { homography.map(it) }
    drawQuad(corners, color)
    corners.forEach { drawCorner(it, color) }
    if (dragged >= 0) drawLoupe(image, box, corners, dragged, color)
}

/**
 * Drags of the calibration's corners: the one nearest where a drag starts,
 * if near enough, moved by as much as the finger is — not to where the finger
 * is, which starts off it, so the corner never jumps under the finger.
 */
private suspend fun PointerInputScope.dragCorners(
    image: ImageBitmap,
    zoom: () -> Zoom,
    onPan: (Offset) -> Unit,
    corners: () -> List<Pt>?,
    onDragged: (Int) -> Unit,
    onMove: (Int, Pt) -> Unit,
) {
    var index = -1
    var panning = false
    var at = Pt(0.0, 0.0)
    detectDragGestures(
        onDragStart = start@{ down ->
            val now = corners() ?: return@start
            val box = viewBox(image, size.toSize(), zoom())
            val nearest = now.indices.minBy { (box.toScreen(now[it]) - down).getDistance() }
            if ((box.toScreen(now[nearest]) - down).getDistance() <= CORNER_REACH.toPx()) {
                index = nearest
                at = now[nearest]
                onDragged(index)
            } else {
                // Away from the corners an enlarged view moves under the finger.
                panning = zoom().scale > 1f
            }
        },
        onDragEnd = {
            index = -1
            panning = false
            onDragged(-1)
        },
        onDragCancel = {
            index = -1
            panning = false
            onDragged(-1)
        },
        onDrag = drag@{ change, delta ->
            if (panning) {
                change.consume()
                onPan(delta)
                return@drag
            }
            if (index < 0) return@drag
            change.consume()
            val box = viewBox(image, size.toSize(), zoom())
            at = Pt(at.x + delta.x / box.width, at.y + delta.y / box.height)
            onMove(index, at)
        },
    )
}

/**
 * Drags of the pictures: the one a drag starts on is selected and follows the
 * finger across the wall — the point under the finger stays under it, the
 * picture keeping its size in centimetres as it goes — and snaps to the
 * others' edges, centres and gaps when it comes within [SNAP_REACH] of one on
 * screen, [onGuides] told what it is held at. [onBegin] and [onEnd] bracket
 * a drag that took a picture.
 */
private suspend fun PointerInputScope.dragPictures(
    image: ImageBitmap,
    zoom: () -> Zoom,
    onPan: (Offset) -> Unit,
    state: () -> EditorState,
    onSelect: (Long?) -> Unit,
    onGuides: (List<Guide>) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onMoveCrop: (Double, Double) -> Unit,
    onResize: (Long, Picture, Int, Pt) -> Unit,
    sizing: () -> Boolean,
    onMove: (Long, Double, Double) -> Unit,
) {
    var id: Long? = null
    var cropping = false
    var panning = false
    // The selected picture as a drag of one of its handles found it, and which handle.
    var resizing: Pair<Picture, Int>? = null
    var grabbed = Pt(0.0, 0.0)
    var from = CmRect(0.0, 0.0, 0.0, 0.0)
    detectDragGestures(
        onDragStart = start@{ down ->
            val now = state()
            val box = viewBox(image, size.toSize(), zoom())
            val toWall = photoToWall(now, box)
            // While sizing, a handle of the selected picture first: it lies on the picture's own edge, where a drag
            // would move it.
            val handle = if (sizing()) selectedHandleAt(now, box, down, CORNER_REACH.toPx()) else null
            if (handle != null && toWall != null) {
                val point = toWall.map(box.toPhoto(down))
                if (point != null) {
                    resizing = handle
                    grabbed = point
                    onBegin()
                    return@start
                }
            }
            val hit = pictureAt(now, box, down)
            if (hit == null) {
                // Off the pictures, a drag moves an enlarged view, or else the crop — when there is one to move.
                if (zoom().scale > 1f) {
                    panning = true
                } else if (now.wall.format.aspect != Aspect.ORIGINAL) {
                    cropping = true
                    onBegin()
                }
                return@start
            }
            val point = toWall?.map(box.toPhoto(down)) ?: return@start
            id = hit
            grabbed = point
            from = now.wall.pictures.first { it.id == hit }.bounds
            onSelect(hit)
            onBegin()
        },
        onDragEnd = {
            if (id != null || cropping || resizing != null) onEnd()
            id = null
            cropping = false
            panning = false
            resizing = null
            onGuides(emptyList())
        },
        onDragCancel = {
            if (id != null || cropping || resizing != null) onEnd()
            id = null
            cropping = false
            panning = false
            resizing = null
            onGuides(emptyList())
        },
        onDrag = drag@{ change, delta ->
            resizing?.let { (picture, handle) ->
                change.consume()
                val box = viewBox(image, size.toSize(), zoom())
                val toWall = photoToWall(state(), box) ?: return@drag
                val point = toWall.map(box.toPhoto(change.position)) ?: return@drag
                // The handle moved by as much as the finger, which came down beside it, not on it.
                val start = picture.bounds.handles[handle]
                onResize(picture.id, picture, handle, Pt(start.x + point.x - grabbed.x, start.y + point.y - grabbed.y))
                return@drag
            }
            if (panning) {
                change.consume()
                onPan(delta)
                return@drag
            }
            if (cropping) {
                change.consume()
                val box = viewBox(image, size.toSize(), zoom())
                onMoveCrop(delta.x / box.width.toDouble(), delta.y / box.height.toDouble())
                return@drag
            }
            val moving = id ?: return@drag
            change.consume()
            val now = state()
            val box = viewBox(image, size.toSize(), zoom())
            val wallToPhoto = now.wall.projection(box.ratio) ?: return@drag
            val toWall = wallToPhoto.inverse() ?: return@drag
            // A finger past the horizon has no place on the wall: the picture waits where it was.
            val point = toWall.map(box.toPhoto(change.position)) ?: return@drag
            val raw = from.copy(left = from.left + point.x - grabbed.x, top = from.top + point.y - grabbed.y)
            // The reach is the same on screen everywhere; in centimetres it grows as the picture goes further off.
            val reach = cmPerPx(raw, wallToPhoto, box)?.let { SNAP_REACH.toPx() * it } ?: 0.0
            val others = now.wall.pictures.filter { it.id != moving }.map { it.bounds }
            val snap = snapPicture(raw, others, reach)
            onMove(moving, snap.left, snap.top)
            onGuides(snap.guides)
        },
    )
}

/** How many centimetres a pixel of screen is across [rect]'s top edge as drawn; null when it is not drawn. */
private fun cmPerPx(rect: CmRect, wallToPhoto: Homography, box: ImageBox): Double? {
    val left = wallToPhoto.map(Pt(rect.left, rect.top))?.let(box::toScreen) ?: return null
    val right = wallToPhoto.map(Pt(rect.right, rect.top))?.let(box::toScreen) ?: return null
    val px = (right - left).getDistance()
    return if (px > 0) rect.width / px else null
}

/** The selected picture and which of its handles is within [reach] of [at] on screen, the nearest; null for none. */
private fun selectedHandleAt(state: EditorState, box: ImageBox, at: Offset, reach: Float): Pair<Picture, Int>? {
    val picture = state.selectedPicture ?: return null
    val homography = state.wall.projection(box.ratio) ?: return null
    val handles = picture.bounds.handles.map { homography.map(it)?.let(box::toScreen) ?: return null }
    val nearest = handles.indices.minBy { (handles[it] - at).getDistance() }
    return if ((handles[nearest] - at).getDistance() <= reach) picture to nearest else null
}

/** A picture's handles on the wall: its corners clockwise from the top left, then the middles of its sides from the top. */
private val CmRect.handles: List<Pt>
    get() = corners + listOf(
        Pt(left + width / 2, top),
        Pt(right, top + height / 2),
        Pt(left + width / 2, bottom),
        Pt(left, top + height / 2),
    )

private const val HANDLE_COUNT = 8

/** The photo's 0..1 coordinates, with the photo in [box], onto the wall's centimetres; null without a calibration. */
private fun photoToWall(state: EditorState, box: ImageBox): Homography? = state.wall.projection(box.ratio)?.inverse()

/** The frontmost picture under [at] with the photo in [box]; null for none, or without a calibration. */
private fun pictureAt(state: EditorState, box: ImageBox, at: Offset): Long? {
    val homography = state.wall.projection(box.ratio) ?: return null
    val point = Pt(at.x.toDouble(), at.y.toDouble())
    return state.wall.pictures.lastOrNull { picture ->
        val quad = picture.bounds.corners.map { corner ->
            val screen = homography.map(corner)?.let(box::toScreen) ?: return@lastOrNull false
            Pt(screen.x.toDouble(), screen.y.toDouble())
        }
        insideConvex(point, quad)
    }?.id
}

/** Where the photo lies in the box: fitted whole, centred. */
internal class ImageBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** The photo's width in heights. */
    val ratio: Double get() = width.toDouble() / height

    fun toScreen(p: Pt) = Offset(left + (p.x * width).toFloat(), top + (p.y * height).toFloat())
    fun toPhoto(o: Offset) = Pt(((o.x - left) / width).toDouble(), ((o.y - top) / height).toDouble())
}

/**
 * How the view is enlarged: [scale] times the photo fitted whole, moved [pan]
 * from centred on the canvas. Along a way it does not fill yet it stays
 * centred.
 */
internal data class Zoom(val scale: Float = 1f, val pan: Offset = Offset.Zero) {
    /**
     * The view after two fingers about [centroid] moved by [pan] and spread by
     * [factor], with the photo in [box] on a canvas of [size]: the point of the
     * photo between them stays between them, as far as the photo's edges let it.
     */
    fun pinched(box: ImageBox, size: Size, centroid: Offset, pan: Offset, factor: Float): Zoom {
        val under = box.toPhoto(centroid)
        val newScale = (scale * factor).coerceIn(1f, MAX_ZOOM)
        val width = box.width / scale * newScale
        val height = box.height / scale * newScale
        val left = centroid.x + pan.x - (under.x * width).toFloat()
        val top = centroid.y + pan.y - (under.y * height).toFloat()
        return Zoom(
            newScale,
            Offset(panWithin(size.width, width, left - (size.width - width) / 2), panWithin(size.height, height, top - (size.height - height) / 2)),
        )
    }
}

/** A [pan] of a photo [zoomed] long on a canvas [length] long, kept so no edge of the photo comes inside the canvas's. */
private fun panWithin(length: Float, zoomed: Float, pan: Float): Float {
    val room = maxOf(0f, (zoomed - length) / 2)
    return pan.coerceIn(-room, room)
}

/** Where the photo lies on a canvas of [size] as the view is enlarged by [zoom]. */
private fun viewBox(image: ImageBitmap, size: Size, zoom: Zoom): ImageBox {
    val fit = fitImage(image, size)
    val width = fit.width * zoom.scale
    val height = fit.height * zoom.scale
    val left = (size.width - width) / 2 + panWithin(size.width, width, zoom.pan.x)
    val top = (size.height - height) / 2 + panWithin(size.height, height, zoom.pan.y)
    return ImageBox(left, top, width, height)
}

/**
 * Two fingers or more: [onPinch] told where they were, how far they moved and
 * how much they spread, and what they do kept from every other gesture — a
 * picture being dragged lets go as the second finger comes down.
 */
private suspend fun PointerInputScope.pinch(onPinch: (centroid: Offset, pan: Offset, factor: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            // Ahead of the other gestures, so a pinch is never also a tap or a drag.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                val pan = event.calculatePan()
                if (factor != 1f || pan != Offset.Zero) onPinch(event.calculateCentroid(useCurrent = false), pan, factor)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
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
