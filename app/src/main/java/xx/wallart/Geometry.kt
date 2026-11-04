package xx.wallart

import kotlin.math.abs

/** A point; in the wall's centimetres or in the photo's 0..1 coordinates, as the caller says. */
data class Pt(val x: Double, val y: Double)

/**
 * A plane-to-plane projection: what a flat wall goes through on its way into
 * a photo. [m] is the 3×3 matrix row by row, scaled so that w is positive
 * for the points in front of the camera.
 */
class Homography private constructor(private val m: DoubleArray) {

    /** Where [p] lands; null for a point on or behind the horizon, which lands nowhere. */
    fun map(p: Pt): Pt? {
        val w = m[6] * p.x + m[7] * p.y + m[8]
        if (w <= EPSILON) return null
        return Pt((m[0] * p.x + m[1] * p.y + m[2]) / w, (m[3] * p.x + m[4] * p.y + m[5]) / w)
    }

    /** The projection back; null when this one flattens the plane onto a line. */
    fun inverse(): Homography? {
        val (a, b, c) = Triple(m[0], m[1], m[2])
        val (d, e, f) = Triple(m[3], m[4], m[5])
        val (g, h, i) = Triple(m[6], m[7], m[8])
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < EPSILON) return null
        // The adjugate: the inverse up to a scale, which a projection does not mind.
        val adj = doubleArrayOf(
            e * i - f * h, c * h - b * i, b * f - c * e,
            f * g - d * i, a * i - c * g, c * d - a * f,
            d * h - e * g, b * g - a * h, a * e - b * d,
        )
        // Scaled by 1/det, not by its last entry: that keeps w positive for the
        // points in front, which map() counts on, whatever the sign of det.
        return Homography(DoubleArray(9) { adj[it] / det })
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

/** Whether [p] lies inside the convex [corners], clockwise on screen (y down), or on their edge. */
fun insideConvex(p: Pt, corners: List<Pt>): Boolean = corners.indices.all { i ->
    val a = corners[i]
    val b = corners[(i + 1) % corners.size]
    (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) >= 0
}
