package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallTest {

    private val door = listOf(Pt(0.30, 0.20), Pt(0.60, 0.28), Pt(0.60, 0.72), Pt(0.30, 0.80))

    @Test
    fun `the inverse takes the photo back to the wall's centimetres`() {
        val h = calibrationHomography(door, 80.0, 200.0)!!
        val back = h.inverse()!!
        for (p in listOf(Pt(0.0, 0.0), Pt(40.0, 100.0), Pt(-30.0, 250.0), Pt(120.0, -40.0))) {
            val there = back.map(h.map(p)!!)
            assertNotNull(there)
            assertEquals(p.x, there!!.x, 1e-6)
            assertEquals(p.y, there.y, 1e-6)
        }
    }

    @Test
    fun `a point is inside a clockwise quadrilateral, on its edge, or outside`() {
        val quad = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(12.0, 8.0), Pt(-1.0, 9.0))
        assertTrue(insideConvex(Pt(5.0, 4.0), quad))
        assertTrue(insideConvex(Pt(5.0, 0.0), quad))
        assertFalse(insideConvex(Pt(5.0, -0.1), quad))
        assertFalse(insideConvex(Pt(13.0, 4.0), quad))
    }

    @Test
    fun `a new picture is as wide as asked, as tall as its artwork makes it, centred`() {
        val picture = newPicture(7, Pt(100.0, 150.0), artAspect = 1.5, artPhoto = "a.jpg", widthCm = 50.0)
        // 44 cm of artwork across, 66 down, a 3 cm frame round it.
        assertEquals(CmRect(75.0, 114.0, 50.0, 72.0), picture.bounds)
        assertEquals(CmRect(78.0, 117.0, 44.0, 66.0), picture.opening)
        assertEquals("a.jpg", picture.artPhoto)
    }

    @Test
    fun `an artwork needs both sides, its frame none or some`() {
        assertTrue(isArtSize(30.0, 40.0, 3.0))
        assertTrue(isArtSize(30.0, 40.0, 0.0))
        assertFalse(isArtSize(0.0, 40.0, 3.0))
        assertFalse(isArtSize(30.0, 40.0, -1.0))
    }

    @Test
    fun `an artwork's size puts the frame round it, centred where the picture was`() {
        val picture = Picture(1, CmRect(0.0, 0.0, 56.0, 76.0)).withArtSize(30.0, 40.0, 3.0)
        assertEquals(CmRect(10.0, 15.0, 36.0, 46.0), picture.bounds)
        assertEquals(CmRect(13.0, 18.0, 30.0, 40.0), picture.opening)
    }

    @Test
    fun `a standard size is known either way round`() {
        assertEquals(ArtSize(30.0, 40.0), standardSizeOf(40.0, 30.0))
        assertEquals("A4", standardSizeOf(21.0, 29.7)?.name)
        assertNull(standardSizeOf(33.0, 40.0))
    }

    @Test
    fun `a frame width may be typed as zero, a size may not`() {
        assertEquals(0.0, parseCm("0", allowZero = true)!!, 0.0)
        assertEquals(0.0, parseCm("0,0", allowZero = true)!!, 0.0)
        assertNull(parseCm("0"))
        assertNull(parseCm("-1", allowZero = true))
    }

    @Test
    fun `pictures restack one place at a time, no further than the ends`() {
        fun pic(id: Long) = Picture(id, CmRect(0.0, 0.0, 10.0, 10.0))
        val wall = Wall(pictures = listOf(pic(1), pic(2), pic(3)))
        assertEquals(listOf(2L, 1L, 3L), wall.restacked(1, 1).pictures.map { it.id })
        assertEquals(listOf(1L, 3L, 2L), wall.restacked(3, -1).pictures.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), wall.restacked(3, 1).pictures.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), wall.restacked(1, -1).pictures.map { it.id })
        assertEquals(4L, wall.nextId())
        assertEquals(1L, Wall().nextId())
    }

    @Test
    fun `a copy goes to the right at the gap the picture has, or at ten centimetres`() {
        fun pic(id: Long, left: Double) = Picture(id, CmRect(left, 0.0, 30.0, 40.0))
        val alone = Wall(pictures = listOf(pic(1, 0.0)))
        val (one, copyId) = alone.duplicated(1)!!
        assertEquals(2L, copyId)
        assertEquals(CmRect(40.0, 0.0, 30.0, 40.0), one.pictures.last().bounds)
        // 6 cm to its left neighbour: the copy keeps that gap on its right.
        val row = Wall(pictures = listOf(pic(1, 0.0), pic(2, 36.0)))
        assertEquals(72.0, row.duplicated(2)!!.first.pictures.last().bounds.left, 1e-9)
        assertNull(alone.duplicated(9))
    }
}
