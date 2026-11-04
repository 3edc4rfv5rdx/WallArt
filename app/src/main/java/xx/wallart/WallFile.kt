package xx.wallart

import android.content.Context
import java.io.File

/**
 * A wall kept in a file of the app's: written whole each time, aside first and
 * moved over the old one, so a write cut short never leaves half a wall.
 */
class WallFile(private val file: File) {

    /** Whether a wall is kept at all, readable or not. */
    fun exists(): Boolean = file.exists()

    /** The wall kept; null when there is none, or none this version can read. */
    fun read(): Wall? = runCatching { file.takeIf { it.exists() }?.readText()?.let(WallJson::read) }.getOrNull()

    /** [wall] kept, over whatever was. Synchronized: the autosave and a save on leaving the app may meet. */
    @Synchronized
    fun write(wall: Wall) {
        val partial = File(file.path + ".part")
        partial.writeText(WallJson.write(wall))
        check(partial.renameTo(file)) { "wall not moved into place" }
    }

    companion object {
        /** The work as it is, written after every change and read back at the next start. */
        fun session(context: Context) = WallFile(File(context.filesDir, "session.json"))

        /** The one draft, written and opened by hand only. Its photos are the same files the work's are. */
        fun draft(context: Context) = WallFile(File(context.filesDir, "draft.json"))
    }
}
