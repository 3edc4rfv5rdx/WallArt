package xx.wallart

import kotlin.math.abs

/**
 * A rectangle of known size on the wall, as the photo shows it: its corners
 * in the photo's 0..1 coordinates — top left, top right, bottom right,
 * bottom left — and its size in centimetres. It ties the wall's centimetres
 * to the photo; the wall's origin is its top left corner.
 */
data class Calibration(val corners: List<Pt>, val widthCm: Double, val heightCm: Double) {

    /** The wall's centimetres onto the photo's 0..1 coordinates; null for a shape no rectangle photographs as. */
    fun wallToPhoto(): Homography? = calibrationHomography(corners, widthCm, heightCm)
}

/** The projection of a [widthCm] × [heightCm] rectangle onto [corners]; null when they cannot be one. */
fun calibrationHomography(corners: List<Pt>, widthCm: Double, heightCm: Double): Homography? {
    if (widthCm <= 0 || heightCm <= 0 || !isConvexClockwise(corners)) return null
    val rect = listOf(Pt(0.0, 0.0), Pt(widthCm, 0.0), Pt(widthCm, heightCm), Pt(0.0, heightCm))
    return Homography.between(rect, corners)
}

/** Where the corners start on a photo never calibrated: a rectangle in the middle. */
val INITIAL_CORNERS = listOf(Pt(0.3, 0.3), Pt(0.7, 0.3), Pt(0.7, 0.7), Pt(0.3, 0.7))

/** A rectangle on the wall, in centimetres from the calibration's top left corner. */
data class CmRect(val left: Double, val top: Double, val width: Double, val height: Double) {
    val right get() = left + width
    val bottom get() = top + height

    /** Its corners clockwise from the top left, as the calibration's are. */
    val corners get() = listOf(Pt(left, top), Pt(right, top), Pt(right, bottom), Pt(left, bottom))

    /** [d] in from every side. */
    fun inset(d: Double) = CmRect(left + d, top + d, width - 2 * d, height - 2 * d)
}

/**
 * One picture on the wall: a frame [bounds] outside, its moulding [mouldingCm]
 * wide, and the artwork filling what the moulding leaves. The frame is
 * [framePhoto] stretched over it when there is one, [frameColor] (ARGB)
 * otherwise; the photos are named as [Photos] keeps them.
 */
data class Picture(
    val id: Long,
    val bounds: CmRect,
    val mouldingCm: Double = DEFAULT_MOULDING_CM,
    val frameColor: Int = DEFAULT_FRAME_COLOR,
    val framePhoto: String? = null,
    val artPhoto: String? = null,
) {
    /** Where the artwork shows: inside the moulding. */
    val opening get() = bounds.inset(mouldingCm)

    /**
     * The picture with its [corner] (0 top left, clockwise) dragged to [to] on
     * the wall, the corner across from it staying where it is: the artwork
     * keeps its shape, is a whole number of centimetres across — no less than
     * [MIN_ART_CM] — and takes a standard size it comes within
     * [STANDARD_SNAP_CM] of; the frame stays as wide.
     */
    fun resizedTo(corner: Int, to: Pt): Picture {
        val anchor = bounds.corners[(corner + 2) % 4]
        val w0 = opening.width
        val h0 = opening.height
        // The artwork the finger asks for, scaled along the artwork's own diagonal: the nearest of its shape.
        val wantW = abs(to.x - anchor.x) - 2 * mouldingCm
        val wantH = abs(to.y - anchor.y) - 2 * mouldingCm
        val scale = (wantW * w0 + wantH * h0) / (w0 * w0 + h0 * h0)
        var w = Math.round(w0 * scale).toDouble().coerceAtLeast(MIN_ART_CM)
        var h = Math.round(w * h0 / w0).toDouble().coerceAtLeast(MIN_ART_CM)
        STANDARD_ART_SIZES.firstOrNull { standard ->
            val (sw, sh) = if (w > h) standard.long to standard.short else standard.short to standard.long
            abs(w - sw) <= STANDARD_SNAP_CM && abs(h - sh) <= STANDARD_SNAP_CM
        }?.let { standard ->
            val landscape = w > h
            w = if (landscape) standard.long else standard.short
            h = if (landscape) standard.short else standard.long
        }
        val outerW = w + 2 * mouldingCm
        val outerH = h + 2 * mouldingCm
        // Away from the anchor, on the side the corner dragged is.
        val left = if (corner == 0 || corner == 3) anchor.x - outerW else anchor.x
        val top = if (corner == 0 || corner == 1) anchor.y - outerH else anchor.y
        return copy(bounds = CmRect(left, top, outerW, outerH))
    }

    /**
     * The picture with the middle of its [side] (0 top, clockwise) dragged to
     * [to] on the wall, the side across from it staying where it is: only that
     * way the artwork changes, in whole centimetres, no less than
     * [MIN_ART_CM], and onto a standard size with the other side when it comes
     * within [STANDARD_SNAP_CM] of one.
     */
    fun resizedSide(side: Int, to: Pt): Picture {
        val across = side == 1 || side == 3
        val b = bounds
        val other = if (across) opening.height else opening.width
        val wanted = when (side) {
            0 -> b.bottom - to.y
            1 -> to.x - b.left
            2 -> to.y - b.top
            else -> b.right - to.x
        } - 2 * mouldingCm
        var art = Math.round(wanted).toDouble().coerceAtLeast(MIN_ART_CM)
        // The standard sizes the other side makes with some length of this one.
        STANDARD_ART_SIZES.flatMap { listOf(it.short to it.long, it.long to it.short) }
            .filter { (fixed, _) -> abs(fixed - other) < 1e-9 }
            .map { (_, length) -> length }
            .minByOrNull { abs(it - art) }
            ?.takeIf { abs(it - art) <= STANDARD_SNAP_CM }
            ?.let { art = it }
        val outer = art + 2 * mouldingCm
        return copy(
            bounds = when (side) {
                0 -> b.copy(top = b.bottom - outer, height = outer)
                1 -> b.copy(width = outer)
                2 -> b.copy(height = outer)
                else -> b.copy(left = b.right - outer, width = outer)
            },
        )
    }

    /** The picture round an artwork [width] × [height] in a frame [moulding] wide, kept centred where it was. */
    fun withArtSize(width: Double, height: Double, moulding: Double): Picture {
        val outerW = width + 2 * moulding
        val outerH = height + 2 * moulding
        val b = bounds
        return copy(
            bounds = CmRect(b.left + (b.width - outerW) / 2, b.top + (b.height - outerH) / 2, outerW, outerH),
            mouldingCm = moulding,
        )
    }
}

const val DEFAULT_MOULDING_CM = 3.0

/** The smallest artwork a picture's corner can be dragged down to. */
const val MIN_ART_CM = 5.0

/** How near a picture's corner, dragged, has to bring its artwork to a standard size to take it. */
const val STANDARD_SNAP_CM = 1.5

/** The gap a copy of a picture is put at beside it, when it has none to a neighbour to go by. */
const val DEFAULT_GAP_CM = 10.0
const val DEFAULT_PICTURE_WIDTH_CM = 50.0

/** A dark wood, near black: the frame most pictures are tried in first. */
const val DEFAULT_FRAME_COLOR = 0xFF2B2622.toInt()

/** Whether an artwork [width] × [height] in a frame [moulding] wide is a picture: both there, the frame none or some. */
fun isArtSize(width: Double, height: Double, moulding: Double): Boolean = width > 0 && height > 0 && moulding >= 0

/**
 * An artwork's standard size, short side by long, in centimetres, as frames
 * and prints are sold; [name] is what it is known by, if anything.
 */
data class ArtSize(val short: Double, val long: Double, val name: String? = null)

/** The sizes artworks and frames commonly come in, smallest first. */
val STANDARD_ART_SIZES = listOf(
    ArtSize(10.0, 15.0),
    ArtSize(13.0, 18.0),
    ArtSize(15.0, 21.0),
    ArtSize(20.0, 30.0),
    ArtSize(21.0, 29.7, "A4"),
    ArtSize(30.0, 40.0),
    ArtSize(30.0, 45.0),
    ArtSize(40.0, 50.0),
    ArtSize(40.0, 60.0),
    ArtSize(50.0, 70.0),
    ArtSize(60.0, 80.0),
    ArtSize(60.0, 90.0),
    ArtSize(70.0, 100.0),
)

/** The standard size [width] × [height] is, either way round; null for one that is none. */
fun standardSizeOf(width: Double, height: Double): ArtSize? = STANDARD_ART_SIZES.firstOrNull {
    it.short == minOf(width, height) && it.long == maxOf(width, height)
}

/**
 * A new picture [widthCm] wide round an artwork of [artAspect] (height over
 * width), centred on [centre]; the height follows from the artwork.
 */
fun newPicture(id: Long, centre: Pt, artAspect: Double, artPhoto: String?, widthCm: Double = DEFAULT_PICTURE_WIDTH_CM): Picture {
    val moulding = DEFAULT_MOULDING_CM
    val height = (widthCm - 2 * moulding) * artAspect + 2 * moulding
    val bounds = CmRect(centre.x - widthCm / 2, centre.y - height / 2, widthCm, height)
    return Picture(id = id, bounds = bounds, mouldingCm = moulding, artPhoto = artPhoto)
}

/**
 * The wall: its [photo] as [Photos] keeps it, its [calibration] once one is
 * made, the [pictures] on it from the back to the front, whether they cast a
 * [shadow], and the [format] the image is saved in.
 */
data class Wall(
    val photo: String? = null,
    val calibration: Calibration? = null,
    val pictures: List<Picture> = emptyList(),
    val shadow: Boolean = true,
    val format: Format = Format(),
) {
    /** [id]'s picture changed by [change]; the rest as they were. */
    fun updated(id: Long, change: (Picture) -> Picture): Wall =
        copy(pictures = pictures.map { if (it.id == id) change(it) else it })

    /** [id]'s picture [by] places to the front (positive) or the back, as far as the ends. */
    fun restacked(id: Long, by: Int): Wall {
        val from = pictures.indexOfFirst { it.id == id }
        if (from < 0) return this
        val to = (from + by).coerceIn(0, pictures.lastIndex)
        val list = pictures.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(pictures = list)
    }

    /** Every photo the wall is made of, by the name [Photos] keeps it by. */
    val photoNames: Set<String>
        get() = (listOf(photo) + pictures.flatMap { listOf(it.artPhoto, it.framePhoto) }).filterNotNull().toSet()

    /**
     * A copy of [id]'s picture put to its right — at the gap it already has to
     * a picture beside it in its row, the nearest such, or [DEFAULT_GAP_CM] —
     * and the copy's id; null when there is no such picture.
     */
    fun duplicated(id: Long): Pair<Wall, Long>? {
        val original = pictures.firstOrNull { it.id == id } ?: return null
        val b = original.bounds
        val gap = pictures.asSequence()
            .filter { it.id != id && it.bounds.top < b.bottom && b.top < it.bounds.bottom }
            .flatMap { sequenceOf(b.left - it.bounds.right, it.bounds.left - b.right) }
            .filter { it > 0 }
            .minOrNull() ?: DEFAULT_GAP_CM
        val copyId = nextId()
        val copy = original.copy(id = copyId, bounds = b.copy(left = b.right + gap))
        return copy(pictures = pictures + copy) to copyId
    }

    /** An id no picture has yet. */
    fun nextId(): Long = (pictures.maxOfOrNull { it.id } ?: 0) + 1
}

/**
 * A step between lines of the calibration grid — 1, 2 or 5 times a power of
 * ten centimetres — that cuts [lengthCm] into no more than [MAX_GRID_LINES] parts.
 */
fun gridStep(lengthCm: Double): Double {
    var step = 1.0
    while (true) {
        for (factor in doubleArrayOf(1.0, 2.0, 5.0)) {
            if (lengthCm / (step * factor) <= MAX_GRID_LINES) return step * factor
        }
        step *= 10
    }
}

private const val MAX_GRID_LINES = 10

/**
 * A length the user typed, in centimetres: a comma or a dot for the fraction;
 * null for anything not above zero — or below it, when [allowZero].
 */
fun parseCm(text: String, allowZero: Boolean = false): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { (it > 0 || allowZero && it == 0.0) && it.isFinite() }

/** [cm] as the size fields show it: no fraction when there is none. */
fun formatCm(cm: Double): String =
    if (cm == Math.rint(cm)) cm.toLong().toString() else cm.toString()
