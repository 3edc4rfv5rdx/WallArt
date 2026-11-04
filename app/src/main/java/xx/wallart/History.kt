package xx.wallart

/**
 * The steps back and forward from the value being edited; the value itself
 * is the caller's. A step is the whole value before a change — cheap, as the
 * values never change once made.
 */
class History<T>(private val limit: Int = HISTORY_LIMIT) {
    private val back = ArrayDeque<T>()
    private val forward = ArrayDeque<T>()

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = forward.isNotEmpty()

    /** [before] as the step back from a change just made; the steps forward lose their meaning, and go. */
    fun record(before: T) {
        back.addLast(before)
        if (back.size > limit) back.removeFirst()
        forward.clear()
    }

    /** The value a step back from [current], which becomes the step forward; null with none. */
    fun undo(current: T): T? {
        val before = back.removeLastOrNull() ?: return null
        forward.addLast(current)
        return before
    }

    /** The value a step forward from [current], which becomes the step back; null with none. */
    fun redo(current: T): T? {
        val after = forward.removeLastOrNull() ?: return null
        back.addLast(current)
        return after
    }

    fun clear() {
        back.clear()
        forward.clear()
    }

    companion object {
        /** How many steps back are kept; the oldest go first. */
        const val HISTORY_LIMIT = 100
    }
}
