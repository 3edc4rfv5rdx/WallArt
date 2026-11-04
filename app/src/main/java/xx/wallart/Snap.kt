package xx.wallart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A line to show while a picture is held snapped, from [from] to [to] in the
 * wall's centimetres: an edge or centre lined up with another picture's, or
 * — with [gapCm] — a gap the same as one already between two pictures.
 */
data class Guide(val from: Pt, val to: Pt, val gapCm: Double? = null)

/** Where a dragged picture goes — its top left corner — and the guides that say why. */
data class Snap(val left: Double, val top: Double, val guides: List<Guide>)

/** Which way a snap works: across, moving left and right, or down, moving up and down. */
private enum class Axis { X, Y }

/** Within this much of each other, two shifts are the same one and both their guides show. */
private const val SAME_SHIFT = 1e-6

/**
 * [moving] snapped to the [others], each way on its own: its left edge,
 * centre or right edge onto one of theirs, or its gap to one of them onto a
 * gap that is already between two of them — whichever is nearest, if within
 * [reach] centimetres. Anything further, and it stays where the finger has it.
 */
fun snapPicture(moving: CmRect, others: List<CmRect>, reach: Double): Snap {
    val x = bestShift(Axis.X, moving, others, reach)
    val y = bestShift(Axis.Y, moving, others, reach)
    val placed = CmRect(moving.left + (x?.shift ?: 0.0), moving.top + (y?.shift ?: 0.0), moving.width, moving.height)
    val guides = listOfNotNull(x, y).flatMap { best -> best.candidates.flatMap { it.guides(placed) } }
    return Snap(placed.left, placed.top, guides)
}

/** One way [moving] could snap along [axis], [shift] centimetres from where it is. */
private sealed class Candidate(val axis: Axis, val shift: Double) {
    abstract fun guides(placed: CmRect): List<Guide>

    /** An edge or the centre lined up with the same one of [other], at [at] along the axis. */
    class Align(axis: Axis, shift: Double, private val other: CmRect, private val at: Double) : Candidate(axis, shift) {
        override fun guides(placed: CmRect): List<Guide> {
            val from = min(placed.start(axis.cross), other.start(axis.cross))
            val to = max(placed.end(axis.cross), other.end(axis.cross))
            return listOf(Guide(point(axis, at, from), point(axis, at, to)))
        }
    }

    /** [gap] to [other], the same as the gap from [before] to [after]. */
    class Gap(
        axis: Axis,
        shift: Double,
        private val other: CmRect,
        private val gap: Double,
        private val before: CmRect,
        private val after: CmRect,
    ) : Candidate(axis, shift) {
        override fun guides(placed: CmRect): List<Guide> {
            val (first, second) = if (placed.mid(axis) > other.mid(axis)) other to placed else placed to other
            return listOf(gapGuide(axis, first, second, gap), gapGuide(axis, before, after, gap))
        }
    }
}

private class Best(val shift: Double, val candidates: List<Candidate>)

/** The nearest of [moving]'s snaps along [axis] within [reach], with every other snap as near; null for none. */
private fun bestShift(axis: Axis, moving: CmRect, others: List<CmRect>, reach: Double): Best? {
    val candidates = alignments(axis, moving, others) + gaps(axis, moving, others)
    val nearest = candidates.filter { abs(it.shift) <= reach }.minByOrNull { abs(it.shift) } ?: return null
    return Best(nearest.shift, candidates.filter { abs(it.shift - nearest.shift) < SAME_SHIFT })
}

/** Start onto start, centre onto centre, end onto end, against each of [others]. */
private fun alignments(axis: Axis, moving: CmRect, others: List<CmRect>): List<Candidate> = others.flatMap { other ->
    listOf(
        moving.start(axis) to other.start(axis),
        moving.mid(axis) to other.mid(axis),
        moving.end(axis) to other.end(axis),
    ).map { (from, to) -> Candidate.Align(axis, to - from, other, to) }
}

/**
 * [moving] beside each of [others] it shares a row with (a column, down),
 * on the side it is on, at each gap already between two pictures of a row.
 */
private fun gaps(axis: Axis, moving: CmRect, others: List<CmRect>): List<Candidate> {
    val existing = others.flatMap { before ->
        others.filter { after ->
            after !== before && overlap(axis.cross, before, after) && after.start(axis) > before.end(axis)
        }.map { after -> Triple(before, after, after.start(axis) - before.end(axis)) }
    }
    return others.filter { overlap(axis.cross, it, moving) }.flatMap { other ->
        existing.map { (before, after, gap) ->
            val shift = if (moving.mid(axis) > other.mid(axis)) {
                other.end(axis) + gap - moving.start(axis)
            } else {
                other.start(axis) - gap - moving.end(axis)
            }
            Candidate.Gap(axis, shift, other, gap, before, after)
        }
    }
}

/** A guide across the gap from [first] to [second] along [axis], halfway along where they face each other. */
private fun gapGuide(axis: Axis, first: CmRect, second: CmRect, gap: Double): Guide {
    val across = (max(first.start(axis.cross), second.start(axis.cross)) + min(first.end(axis.cross), second.end(axis.cross))) / 2
    return Guide(point(axis, first.end(axis), across), point(axis, second.start(axis), across), gap)
}

/** Whether [a] and [b] share some of their extent along [axis]. */
private fun overlap(axis: Axis, a: CmRect, b: CmRect): Boolean = a.start(axis) < b.end(axis) && b.start(axis) < a.end(axis)

private val Axis.cross get() = if (this == Axis.X) Axis.Y else Axis.X

private fun CmRect.start(axis: Axis) = if (axis == Axis.X) left else top
private fun CmRect.end(axis: Axis) = if (axis == Axis.X) right else bottom
private fun CmRect.mid(axis: Axis) = (start(axis) + end(axis)) / 2

/** The point [along] the axis and [across] it. */
private fun point(axis: Axis, along: Double, across: Double) = if (axis == Axis.X) Pt(along, across) else Pt(across, along)
