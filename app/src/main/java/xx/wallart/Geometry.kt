package xx.wallart

import kotlin.math.abs

/** A point; in the wall's centimetres or in the photo's 0..1 coordinates, as the caller says. */
data class Pt(val x: Double, val y: Double)

/**
 * A plane-to-plane projection: what a flat wall goes through on its way into
 * a photo. [m] is the 3×3 matrix row by row, its last entry 1.
 */
class Homography private constructor(private val m: DoubleArray) {

    /** Where [p] lands; null for a point on or behind the horizon, which lands nowhere. */
    fun map(p: Pt): Pt? {
        val w = m[6] * p.x + m[7] * p.y + m[8]
        if (w <= EPSILON) return null
        return Pt((m[0] * p.x + m[1] * p.y + m[2]) / w, (m[3] * p.x + m[4] * p.y + m[5]) / w)
    }

    companion object {
        private const val EPSILON = 1e-12

        /**
         * The projection taking each of [from] to the matching one of [to], four
         * points each; null when either four are degenerate — three on a line.
         */
        fun between(from: List<Pt>, to: List<Pt>): Homography? {
            require(from.size == 4 && to.size == 4) { "four points each" }
            // Eight equations in the eight unknowns h0..h7, h8 being 1.
            val a = Array(8) { DoubleArray(9) }
            for (k in 0 until 4) {
                val (x, y) = from[k]
                val (u, v) = to[k]
                a[2 * k] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * k + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val h = solve(a) ?: return null
            return Homography(h + 1.0)
        }

        /** Gaussian elimination with partial pivoting on the augmented [a]; null for a singular system. */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (col in 0 until n) {
                val pivot = (col until n).maxBy { abs(a[it][col]) }
                if (abs(a[pivot][col]) < EPSILON) return null
                a[col] = a[pivot].also { a[pivot] = a[col] }
                for (row in 0 until n) {
                    if (row == col) continue
                    val factor = a[row][col] / a[col][col]
                    if (factor == 0.0) continue
                    for (k in col..n) a[row][k] -= factor * a[col][k]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}

/**
 * Whether [corners], in order, make a convex quadrilateral turning clockwise on
 * screen (y down) — the only shape a rectangle on a wall can be photographed as.
 */
fun isConvexClockwise(corners: List<Pt>): Boolean {
    if (corners.size != 4) return false
    return corners.indices.all { i ->
        val a = corners[i]
        val b = corners[(i + 1) % 4]
        val c = corners[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        cross > 0
    }
}
