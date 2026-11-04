package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test
    fun `steps go back and forward in order`() {
        val history = History<Int>()
        history.record(1)
        history.record(2)
        // Now at 3.
        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertNull(history.undo(1))
        assertEquals(2, history.redo(1))
        assertEquals(3, history.redo(2))
        assertNull(history.redo(3))
    }

    @Test
    fun `a change after a step back drops the steps forward`() {
        val history = History<Int>()
        history.record(1)
        assertEquals(1, history.undo(2))
        assertTrue(history.canRedo)
        history.record(1)
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun `only the newest steps are kept`() {
        val history = History<Int>(limit = 2)
        history.record(1)
        history.record(2)
        history.record(3)
        assertEquals(3, history.undo(4))
        assertEquals(2, history.undo(3))
        assertNull(history.undo(2))
    }
}
