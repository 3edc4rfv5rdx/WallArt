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

    private val photoRatio = 4.0 / 3

    @Test
    fun `straight frames are square to the photo`() {
        val h = Calibration(door, 80.0, 200.0).projection(straight = true, photoRatio)!!
        val topLeft = h.map(Pt(-30.0, -40.0))!!
        val topRight = h.map(Pt(150.0, -40.0))!!
        val bottomRight = h.map(Pt(150.0, 300.0))!!
        val bottomLeft = h.map(Pt(-30.0, 300.0))!!
        assertEquals(topLeft.y, topRight.y, 1e-9)
        assertEquals(bottomLeft.y, bottomRight.y, 1e-9)
        assertEquals(topLeft.x, bottomLeft.x, 1e-9)
        assertEquals(topRight.x, bottomRight.x, 1e-9)
    }

    @Test
    fun `straight frames keep a centimetre as long across as down`() {
        val h = Calibration(door, 80.0, 200.0).projection(straight = true, photoRatio)!!
        val origin = h.map(Pt(0.0, 0.0))!!
        val across = (h.map(Pt(10.0, 0.0))!!.x - origin.x) * photoRatio
        val down = h.map(Pt(0.0, 10.0))!!.y - origin.y
        assertEquals(across, down, 1e-9)
        assertTrue(across > 0)
    }

    @Test
    fun `straight frames keep the rectangle's middle and the area of its corners`() {
        val calibration = Calibration(door, 80.0, 200.0)
        val h = calibration.projection(straight = true, photoRatio)!!
        assertNear(calibration.wallToPhoto()!!.map(Pt(40.0, 100.0))!!, h.map(Pt(40.0, 100.0)))
        val width = h.map(Pt(80.0, 0.0))!!.x - h.map(Pt(0.0, 0.0))!!.x
        val height = h.map(Pt(0.0, 200.0))!!.y - h.map(Pt(0.0, 0.0))!!.y
        // The door's trapezoid: sides 0.6 and 0.44 long, 0.3 apart.
        assertEquals(0.156, width * height, 1e-9)
    }

    @Test
    fun `straight frames lead back from the photo to the wall`() {
        val h = Calibration(door, 80.0, 200.0).projection(straight = true, photoRatio)!!
        val point = Pt(-120.0, 350.0)
        assertNear(point, h.inverse()!!.map(h.map(point)!!))
    }

    @Test
    fun `without straight frames the wall's perspective stays`() {
        val h = Calibration(door, 80.0, 200.0).projection(straight = false, photoRatio)!!
        assertNear(door[1], h.map(Pt(80.0, 0.0)))
    }

    @Test
    fun `corners no rectangle photographs as make no straight frames either`() {
        val crossed = listOf(door[0], door[2], door[1], door[3])
        assertNull(Calibration(crossed, 80.0, 200.0).projection(straight = true, photoRatio))
    }

    @Test
    fun `a wall follows its straight flag, and has no projection without a calibration`() {
        val calibration = Calibration(door, 80.0, 200.0)
        assertNull(Wall().projection(photoRatio))
        assertNear(door[1], Wall(calibration = calibration).projection(photoRatio)!!.map(Pt(80.0, 0.0)))
        val straight = Wall(calibration = calibration, straight = true).projection(photoRatio)!!
        assertEquals(straight.map(Pt(0.0, 0.0))!!.y, straight.map(Pt(80.0, 0.0))!!.y, 1e-9)
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
