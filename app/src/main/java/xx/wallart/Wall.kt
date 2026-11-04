package xx.wallart

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
}

const val DEFAULT_MOULDING_CM = 3.0
const val DEFAULT_PICTURE_WIDTH_CM = 50.0

/** A dark wood, near black: the frame most pictures are tried in first. */
const val DEFAULT_FRAME_COLOR = 0xFF2B2622.toInt()

/** Whether [width] × [height] with a [moulding] wide frame is a picture: some artwork left inside the frame. */
fun isPictureSize(width: Double, height: Double, moulding: Double): Boolean =
    width > 0 && height > 0 && moulding >= 0 && 2 * moulding < minOf(width, height)

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
