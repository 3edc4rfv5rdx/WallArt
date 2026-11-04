package xx.wallart

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The shapes the saved image is offered in, each by its short and long side,
 * with the long sides in pixels it can be saved at and the one it starts at —
 * the same as PhotoCollage's. [ORIGINAL] is the wall photo's own shape, which
 * crops nothing.
 */
enum class Aspect(val short: Double, val long: Double, val sizes: List<Int>, val defaultLong: Int) {
    ORIGINAL(0.0, 0.0, listOf(1080, 2048, 4096), 2048),
    SQUARE(1.0, 1.0, listOf(1080, 2048, 4096), 2048),
    FOUR_FIVE(4.0, 5.0, listOf(1350, 2500, 5000), 2500),
    THREE_FOUR(3.0, 4.0, listOf(1440, 2048, 4032), 2048),
    TWO_THREE(2.0, 3.0, listOf(1620, 1800, 3600), 1800),
    NINE_SIXTEEN(9.0, 16.0, listOf(1280, 1920, 2560, 3840), 1920),
    A4(210.0, 297.0, listOf(1754, 3508), 3508),
    ;

    init {
        check(defaultLong in sizes) { "$name starts at a size it does not offer" }
    }
}

/**
 * A named standard: a shape, its way round and a size in one pick. Labels
 * are not translated: they are names. Every size is one its shape offers.
 */
enum class Preset(val label: String, val aspect: Aspect, val landscape: Boolean, val longSide: Int) {
    INSTAGRAM_SQUARE("Instagram 1:1", Aspect.SQUARE, false, 1080),
    INSTAGRAM_PORTRAIT("Instagram 4:5", Aspect.FOUR_FIVE, false, 1350),
    STORIES("Stories 9:16", Aspect.NINE_SIXTEEN, false, 1920),
    HD("HD", Aspect.NINE_SIXTEEN, true, 1280),
    FULL_HD("Full HD", Aspect.NINE_SIXTEEN, true, 1920),
    QHD("2K", Aspect.NINE_SIXTEEN, true, 2560),
    UHD("4K", Aspect.NINE_SIXTEEN, true, 3840),
    PHOTO_10X15("10×15 300 dpi", Aspect.TWO_THREE, true, 1800),
    A4_150("A4 150 dpi", Aspect.A4, false, 1754),
    A4_300("A4 300 dpi", Aspect.A4, false, 3508),
    ;

    init {
        check(longSide in aspect.sizes) { "$name is a size its shape does not offer" }
    }
}

/** A part of the wall photo, in its 0..1 coordinates. */
data class Crop(val left: Double, val top: Double, val width: Double, val height: Double)

/**
 * What is saved of the wall: its [aspect], which way round it is
 * ([landscape]; the original shape is the photo's own way round), its
 * [longSide] in pixels, and where the crop lies in the room the photo leaves
 * it — [cropX] and [cropY] from 0, against the left or top edge, to 1,
 * against the right or bottom, a half in the middle.
 */
data class Format(
    val aspect: Aspect = Aspect.ORIGINAL,
    val landscape: Boolean = false,
    val longSide: Int = aspect.defaultLong,
    val cropX: Double = 0.5,
    val cropY: Double = 0.5,
) {
    /** Width over height of the image saved from a photo of [photoRatio] (width over height). */
    fun ratio(photoRatio: Double): Double = when (aspect) {
        Aspect.ORIGINAL -> photoRatio
        else -> if (landscape) aspect.long / aspect.short else aspect.short / aspect.long
    }

    /** The part of a photo of [photoRatio] saved: as much of it as the shape takes, where the crop lies. */
    fun crop(photoRatio: Double): Crop {
        val r = ratio(photoRatio)
        // The whole height of a photo wider than the shape, the whole width of one taller.
        val (w, h) = if (photoRatio > r) r / photoRatio to 1.0 else 1.0 to photoRatio / r
        return Crop((1 - w) * cropX, (1 - h) * cropY, w, h)
    }

    /** The saved image's size, width by height: [longSide] on the long side, the other by the shape. */
    fun savedSize(photoRatio: Double, longSide: Int = this.longSide): Pair<Int, Int> {
        val r = ratio(photoRatio)
        return if (r >= 1) {
            longSide to maxOf(1, (longSide / r).roundToInt())
        } else {
            maxOf(1, (longSide * r).roundToInt()) to longSide
        }
    }

    /**
     * The format at the size of its shape nearest to what its crop of a photo
     * [photoW] × [photoH] really is: no more scaled than the shape's sizes make
     * it. A tie goes to the larger.
     */
    fun fitted(photoW: Int, photoH: Int): Format {
        val c = crop(photoW.toDouble() / photoH)
        val real = maxOf(c.width * photoW, c.height * photoH)
        return copy(longSide = aspect.sizes.sortedDescending().minBy { abs(it - real) })
    }

    fun withPreset(preset: Preset): Format = copy(aspect = preset.aspect, landscape = preset.landscape, longSide = preset.longSide)

    /** The preset this format makes, or null. A square is the same either way round. */
    fun preset(): Preset? = Preset.entries.firstOrNull {
        it.aspect == aspect && it.longSide == longSide && (it.landscape == landscape || aspect == Aspect.SQUARE)
    }

    /**
     * The crop moved by [dx], [dy] in the photo's 0..1 coordinates, kept on the
     * photo; a way the crop already fills cannot move.
     */
    fun cropMoved(photoRatio: Double, dx: Double, dy: Double): Format {
        val c = crop(photoRatio)
        val roomX = 1 - c.width
        val roomY = 1 - c.height
        return copy(
            cropX = if (roomX > EPSILON) ((c.left + dx) / roomX).coerceIn(0.0, 1.0) else cropX,
            cropY = if (roomY > EPSILON) ((c.top + dy) / roomY).coerceIn(0.0, 1.0) else cropY,
        )
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
