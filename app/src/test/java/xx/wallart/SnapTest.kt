package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapTest {

    private val a = CmRect(0.0, 0.0, 40.0, 60.0)
    private val b = CmRect(50.0, 0.0, 40.0, 60.0)

    @Test
    fun `far from everything, the picture stays where the finger has it`() {
        val snap = snapPicture(CmRect(200.0, 200.0, 30.0, 30.0), listOf(a), reach = 2.0)
        assertEquals(200.0, snap.left, 0.0)
        assertEquals(200.0, snap.top, 0.0)
        assertTrue(snap.guides.isEmpty())
    }

    @Test
    fun `a top near another's snaps onto it, with a guide along it`() {
        val snap = snapPicture(CmRect(100.0, 1.5, 30.0, 30.0), listOf(a), reach = 2.0)
        assertEquals(100.0, snap.left, 0.0)
        assertEquals(0.0, snap.top, 0.0)
        assertEquals(listOf(Guide(Pt(0.0, 0.0), Pt(130.0, 0.0))), snap.guides)
    }

    @Test
    fun `centres snap as edges do`() {
        // Centre 31 down against a's 30.
        val snap = snapPicture(CmRect(100.0, 21.0, 30.0, 20.0), listOf(a), reach = 2.0)
        assertEquals(20.0, snap.top, 0.0)
    }

    @Test
    fun `the nearest snap wins`() {
        // Top 1 off a's top, bottom 0.5 off a's bottom: the bottom wins.
        val snap = snapPicture(CmRect(100.0, 1.0, 30.0, 59.5), listOf(a), reach = 2.0)
        assertEquals(0.5, snap.top, 1e-9)
    }

    @Test
    fun `a picture beside a row takes the gap already in it`() {
        // a and b are 10 apart; c to the right of b at 11.5 comes in to 10.
        val snap = snapPicture(CmRect(101.5, 0.0, 40.0, 60.0), listOf(a, b), reach = 2.0)
        assertEquals(100.0, snap.left, 1e-9)
        val gaps = snap.guides.filter { it.gapCm != null }
        assertTrue(gaps.any { it.from.x == 90.0 && it.to.x == 100.0 && it.gapCm == 10.0 })
        assertTrue(gaps.any { it.from.x == 40.0 && it.to.x == 50.0 && it.gapCm == 10.0 })
    }

    @Test
    fun `a gap to the left of a row is taken the same way`() {
        val snap = snapPicture(CmRect(-51.0, 0.0, 40.0, 60.0), listOf(a, b), reach = 2.0)
        assertEquals(-50.0, snap.left, 1e-9)
    }

    @Test
    fun `no gap is taken from pictures not in a row with it`() {
        // Far below a and b: shares no row with them, so their gap means nothing to it.
        val snap = snapPicture(CmRect(101.0, 200.0, 40.0, 60.0), listOf(a, b), reach = 2.0)
        assertEquals(101.0, snap.left, 0.0)
    }
}
