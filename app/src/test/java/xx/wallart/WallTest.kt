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
    fun `a new picture is its artwork and frame, centred where asked`() {
        val picture = newPicture(7, Pt(100.0, 150.0), ArtSpec(30.0, 40.0, 3.0), artPhoto = "a.jpg")
        assertEquals(CmRect(82.0, 127.0, 36.0, 46.0), picture.bounds)
        assertEquals(ArtSpec(30.0, 40.0, 3.0), picture.spec)
        assertEquals("a.jpg", picture.artPhoto)
        assertNull(newPicture(8, Pt(0.0, 0.0), DEFAULT_ART_SPEC, artPhoto = null).artPhoto)
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

    @Test
    fun `a corner dragged keeps the corner across, the artwork's shape and whole centimetres`() {
        // A 30 × 40 artwork in a 3 cm frame, its top left at 0, 0: 36 × 46 outside.
        val picture = Picture(1, CmRect(0.0, 0.0, 36.0, 46.0), mouldingCm = 3.0)
        // The bottom right dragged out to make about 45 × 60 of artwork.
        val bigger = picture.resizedTo(2, Pt(51.4, 66.2))
        assertEquals(CmRect(0.0, 0.0, 51.0, 66.0), bigger.bounds)
        // The top left dragged in: the bottom right stays at 36, 46.
        val smaller = picture.resizedTo(0, Pt(12.0, 15.0))
        assertEquals(36.0, smaller.bounds.right, 1e-9)
        assertEquals(46.0, smaller.bounds.bottom, 1e-9)
        assertEquals(smaller.opening.width / smaller.opening.height, 0.75, 0.03)
    }

    @Test
    fun `a corner dragged near a standard size takes it, and no smaller than the least`() {
        val picture = Picture(1, CmRect(0.0, 0.0, 36.0, 46.0), mouldingCm = 3.0)
        // 41 × 55 asked: 40 × 50 is 5 off on the long side, too far to take.
        assertEquals(41.0, picture.resizedTo(2, Pt(47.0, 61.0)).opening.width, 1e-9)
        // 22 × 29 asked: A4's 21 × 29.7 is within reach on both sides, and taken.
        val a4 = picture.resizedTo(2, Pt(28.0, 35.3)).opening
        assertEquals(21.0, a4.width, 1e-9)
        assertEquals(29.7, a4.height, 1e-9)
        assertEquals(MIN_ART_CM, picture.resizedTo(2, Pt(1.0, 1.0)).opening.width, 1e-9)
    }

    @Test
    fun `a side dragged changes only that way, the side across staying`() {
        // A 30 × 40 artwork in a 3 cm frame: 36 × 46 outside.
        val picture = Picture(1, CmRect(10.0, 20.0, 36.0, 46.0), mouldingCm = 3.0)
        // The bottom down to 47.6 cm of artwork: rounds to 48, then 30 × 45 is 3 off — too far.
        val longer = picture.resizedSide(2, Pt(0.0, 20.0 + 6.0 + 47.6))
        assertEquals(CmRect(10.0, 20.0, 36.0, 54.0), longer.bounds)
        // The bottom down to 44.2: 30 × 45 is within reach, and taken.
        assertEquals(45.0, picture.resizedSide(2, Pt(0.0, 20.0 + 6.0 + 44.2)).opening.height, 1e-9)
        // The left side in: the right stays at 46.
        val narrower = picture.resizedSide(3, Pt(25.0, 0.0))
        assertEquals(46.0, narrower.bounds.right, 1e-9)
        assertEquals(15.0, narrower.opening.width, 1e-9)
    }
}
