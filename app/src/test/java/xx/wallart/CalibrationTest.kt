package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {

    private fun assertNear(expected: Pt, actual: Pt?) {
        assertNotNull(actual)
        assertEquals(expected.x, actual!!.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
    }

    /** A door seen from the left: its far side shorter. */
    private val door = listOf(Pt(0.30, 0.20), Pt(0.60, 0.28), Pt(0.60, 0.72), Pt(0.30, 0.80))

    @Test
    fun `the rectangle's corners land on the corners placed`() {
        val h = calibrationHomography(door, 80.0, 200.0)!!
        assertNear(door[0], h.map(Pt(0.0, 0.0)))
        assertNear(door[1], h.map(Pt(80.0, 0.0)))
        assertNear(door[2], h.map(Pt(80.0, 200.0)))
        assertNear(door[3], h.map(Pt(0.0, 200.0)))
    }

    @Test
    fun `the rectangle's centre lands where its diagonals cross, not halfway across`() {
        val h = calibrationHomography(door, 80.0, 200.0)!!
        val centre = h.map(Pt(40.0, 100.0))!!
        // The diagonals of this symmetric trapezoid cross on its axis, nearer the far side.
        assertEquals(0.5, centre.y, 1e-9)
        assertTrue(centre.x > 0.45)
    }

    @Test
    fun `a square-on photo maps the wall linearly`() {
        val corners = listOf(Pt(0.2, 0.1), Pt(0.6, 0.1), Pt(0.6, 0.5), Pt(0.2, 0.5))
        val h = calibrationHomography(corners, 40.0, 40.0)!!
        assertNear(Pt(0.3, 0.2), h.map(Pt(10.0, 10.0)))
        // Past the rectangle as well: the wall goes on beyond it.
        assertNear(Pt(0.8, 0.1), h.map(Pt(60.0, 0.0)))
    }

    @Test
    fun `crossed, inside-out and flat corners make no calibration`() {
        val crossed = listOf(door[0], door[2], door[1], door[3])
        val anticlockwise = door.reversed()
        val flat = listOf(Pt(0.1, 0.1), Pt(0.5, 0.1), Pt(0.9, 0.1), Pt(0.5, 0.5))
        assertNull(calibrationHomography(crossed, 80.0, 200.0))
        assertNull(calibrationHomography(anticlockwise, 80.0, 200.0))
        assertNull(calibrationHomography(flat, 80.0, 200.0))
        assertFalse(isConvexClockwise(crossed))
        assertTrue(isConvexClockwise(INITIAL_CORNERS))
    }

    @Test
    fun `no size makes no calibration`() {
        assertNull(calibrationHomography(door, 0.0, 200.0))
        assertNull(calibrationHomography(door, 80.0, -1.0))
    }

    @Test
    fun `a calibration edit makes one only with both sizes typed in`() {
        val edit = CalibrationEdit(door, "80", "")
        assertNull(edit.result)
        val done = edit.copy(heightText = "200,5")
        assertEquals(Calibration(door, 80.0, 200.5), done.result)
    }

    @Test
    fun `an edit from a calibration shows its size as it was typed`() {
        val edit = CalibrationEdit.from(Calibration(door, 80.0, 200.5))
        assertEquals("80", edit.widthText)
        assertEquals("200.5", edit.heightText)
        assertEquals(CalibrationEdit(INITIAL_CORNERS, "", ""), CalibrationEdit.from(null))
    }

    @Test
    fun `sizes are read with a comma or a dot, and nothing at or below zero`() {
        assertEquals(12.5, parseCm(" 12,5 ")!!, 0.0)
        assertEquals(12.5, parseCm("12.5")!!, 0.0)
        assertNull(parseCm(""))
        assertNull(parseCm("0"))
        assertNull(parseCm("-3"))
        assertNull(parseCm("abc"))
    }

    @Test
    fun `the grid step is round and cuts the side into at most ten parts`() {
        assertEquals(1.0, gridStep(4.0), 0.0)
        assertEquals(1.0, gridStep(10.0), 0.0)
        assertEquals(2.0, gridStep(11.0), 0.0)
        assertEquals(20.0, gridStep(200.0), 0.0)
        assertEquals(50.0, gridStep(210.0), 0.0)
        assertEquals(100.0, gridStep(1000.0), 0.0)
    }
}
