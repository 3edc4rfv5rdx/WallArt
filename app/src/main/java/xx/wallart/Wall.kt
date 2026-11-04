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

/** The wall: its [calibration] once one is made. Its photo is a file of the app's ([WallPhoto]). */
data class Wall(val calibration: Calibration? = null)

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

/** A length the user typed, in centimetres: a comma or a dot for the fraction; null for anything not above zero. */
fun parseCm(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

/** [cm] as the size fields show it: no fraction when there is none. */
fun formatCm(cm: Double): String =
    if (cm == Math.rint(cm)) cm.toLong().toString() else cm.toString()
