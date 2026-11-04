package xx.wallart

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The calibration being made: the corners as dragged so far and the size as
 * typed so far. Nothing of it reaches the wall until it is applied.
 */
data class CalibrationEdit(val corners: List<Pt>, val widthText: String, val heightText: String) {
    /** The calibration this would make; null while the corners or the size cannot be one. */
    val result: Calibration?
        get() {
            val width = parseCm(widthText) ?: return null
            val height = parseCm(heightText) ?: return null
            return Calibration(corners, width, height).takeIf { it.wallToPhoto() != null }
        }

    companion object {
        /** An edit starting from [calibration], or from the middle of the photo with no size when there is none. */
        fun from(calibration: Calibration?): CalibrationEdit = if (calibration == null) {
            CalibrationEdit(INITIAL_CORNERS, "", "")
        } else {
            CalibrationEdit(calibration.corners, formatCm(calibration.widthCm), formatCm(calibration.heightCm))
        }
    }
}

/** Which of a picture's photos a pick is for. */
enum class PhotoSlot { ART, FRAME }

/**
 * Everything the editor shows. [photos] are the wall's photos as shown, by
 * the name the wall keeps them by; [calibrating] the calibration under way,
 * if one is; [selected] the picture the tools work on; whether there are
 * steps to [canUndo] and [canRedo]. Busy from the start: the work kept is
 * read back first.
 */
data class EditorState(
    val wall: Wall = Wall(),
    val photos: Map<String, ImageBitmap> = emptyMap(),
    val calibrating: CalibrationEdit? = null,
    val selected: Long? = null,
    val busy: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    /** The wall's photo as shown; null while there is none, or it cannot be read. */
    val image: ImageBitmap? get() = wall.photo?.let { photos[it] }

    val selectedPicture: Picture? get() = wall.pictures.firstOrNull { it.id == selected }
}

/** How long the wall has to stay as it is before it is written: a drag is one write, not one a frame. */
private const val AUTOSAVE_DELAY_MS = 400L

class WallViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** One-off lines for the user, each said once. */
    private val _notices = Channel<Int>(Channel.BUFFERED)
    val notices = _notices.receiveAsFlow()

    private val session = WallFile.session(application)

    /** The walls before and after the one shown, a step for each change of it. */
    private val history = History<Wall>()

    /** The wall as a picture's drag found it, while one is under way: the drag goes in as one step. */
    private var dragStart: Wall? = null

    /** Whether the work kept has been read back: till then there is nothing of the user's to write. */
    @Volatile
    private var restored = false

    init {
        viewModelScope.launch {
            restore()
            autosave()
        }
    }

    /**
     * The work as it was left, with its photos; the photos no longer part of
     * it deleted — nothing else can name them at a start, as the steps to
     * undo are not kept. Not when the work kept cannot be read: what it
     * names is not known, and its photos are all there is left of it.
     */
    private suspend fun restore() {
        val context = getApplication<Application>()
        val (wall, photos) = withContext(Dispatchers.IO) {
            val kept = session.read()
            if (kept != null || !session.exists()) Photos.sweep(context, kept?.photoNames.orEmpty())
            val wall = kept ?: Wall()
            wall to loadPhotos(context, wall)
        }
        restored = true
        _state.update { it.copy(wall = wall, photos = photos, busy = false) }
    }

    /** The wall written each time it changes and then stays as it is for a moment. */
    private suspend fun autosave() {
        _state.map { it.wall }.distinctUntilChanged().drop(1).collectLatest { wall ->
            delay(AUTOSAVE_DELAY_MS)
            withContext(Dispatchers.IO) { writeSession(wall) }
        }
    }

    /** The wall written now, as the app goes out of sight: the autosave may not get its moment. */
    fun saveNow() {
        if (restored) writeSession(_state.value.wall)
    }

    private fun writeSession(wall: Wall) {
        // A write that fails leaves the last one standing; the next change tries again.
        runCatching { session.write(wall) }
    }

    /**
     * [uri] as the wall's photo. A new photo is a new view of the wall, so the
     * old calibration says nothing about it: the calibration starts over, from
     * the size the last one had. The pictures stay where they are on the wall.
     */
    fun importPhoto(uri: Uri) = importing({ Photos.import(getApplication(), uri, wall = true) }) { state, (name, bitmap) ->
        val old = state.wall.calibration
        state.copy(
            wall = state.wall.copy(photo = name, calibration = null),
            photos = state.photos + (name to bitmap.asImageBitmap()),
            calibrating = CalibrationEdit.from(old).copy(corners = INITIAL_CORNERS),
            selected = null,
        )
    }

    /**
     * A new picture round the artwork at [uri], in the middle of the photo,
     * its height following the artwork's shape; selected, to be worked on.
     */
    fun addPicture(uri: Uri) = importing({ Photos.import(getApplication(), uri, wall = false) }) { state, (name, bitmap) ->
        val calibration = state.wall.calibration ?: return@importing state
        val id = state.wall.nextId()
        val picture = newPicture(id, photoCentreOnWall(calibration), bitmap.height.toDouble() / bitmap.width, name)
        state.copy(
            wall = state.wall.copy(pictures = state.wall.pictures + picture),
            photos = state.photos + (name to bitmap.asImageBitmap()),
            selected = id,
        )
    }

    /** [uri] as the [slot] photo of the selected picture; its size stays, the artwork cropped to it. */
    fun setPicturePhoto(slot: PhotoSlot, uri: Uri) {
        val id = _state.value.selected ?: return
        importing({ Photos.import(getApplication(), uri, wall = false) }) { state, (name, bitmap) ->
            state.copy(
                wall = state.wall.updated(id) {
                    when (slot) {
                        PhotoSlot.ART -> it.copy(artPhoto = name)
                        PhotoSlot.FRAME -> it.copy(framePhoto = name)
                    }
                },
                photos = state.photos + (name to bitmap.asImageBitmap()),
            )
        }
    }

    /** The selected picture's frame in [color] (ARGB), its frame photo, if any, dropped for it. */
    fun setFrameColor(color: Int) = updateSelected { it.copy(frameColor = color, framePhoto = null) }

    /** The selected picture [widthCm] × [heightCm] outside, with a [mouldingCm] frame; kept centred where it was. */
    fun setSize(widthCm: Double, heightCm: Double, mouldingCm: Double) {
        if (!isPictureSize(widthCm, heightCm, mouldingCm)) return
        updateSelected {
            val b = it.bounds
            val bounds = CmRect(b.left + (b.width - widthCm) / 2, b.top + (b.height - heightCm) / 2, widthCm, heightCm)
            it.copy(bounds = bounds, mouldingCm = mouldingCm)
        }
    }

    /** A picture's drag starting: what it moves is one step, however many moves it makes. */
    fun beginDrag() {
        dragStart = _state.value.wall
    }

    /** A picture's drag let go: the wall it started from a step back, if it moved anything. */
    fun endDrag() {
        val start = dragStart ?: return
        dragStart = null
        if (_state.value.wall != start) {
            history.record(start)
            _state.value = withHistory(_state.value)
        }
    }

    /** Picture [id] with its top left corner at [left], [top] on the wall; a step only with the drag it is part of. */
    fun movePicture(id: Long, left: Double, top: Double) {
        _state.update { state ->
            state.copy(wall = state.wall.updated(id) { it.copy(bounds = it.bounds.copy(left = left, top = top)) })
        }
    }

    fun select(id: Long?) {
        _state.update { it.copy(selected = id) }
    }

    /** The selected picture [by] places to the front (positive) or the back. */
    fun restackSelected(by: Int) {
        val id = _state.value.selected ?: return
        commit { it.copy(wall = it.wall.restacked(id, by)) }
    }

    fun removeSelected() {
        commit { state ->
            val id = state.selected ?: return@commit state
            state.copy(wall = state.wall.copy(pictures = state.wall.pictures.filterNot { it.id == id }), selected = null)
        }
    }

    fun toggleShadow() {
        commit { it.copy(wall = it.wall.copy(shadow = !it.wall.shadow)) }
    }

    /** The wall a step back; not while a drag, a calibration or a photo coming in is under way. */
    fun undo() = step(history::undo)

    /** The wall a step forward, on the same terms as [undo]. */
    fun redo() = step(history::redo)

    private fun step(take: (Wall) -> Wall?) {
        val state = _state.value
        if (dragStart != null || state.calibrating != null || state.busy) return
        val wall = take(state.wall) ?: return
        // The selection kept while its picture is still there.
        val selected = state.selected?.takeIf { id -> wall.pictures.any { it.id == id } }
        _state.value = pruned(withHistory(state.copy(wall = wall, selected = selected)))
        loadMissingPhotos()
    }

    /** Whether the wall still needs calibrating before a picture can go on it; if so, says so and starts it. */
    fun calibrateFirst(): Boolean {
        if (_state.value.wall.calibration != null) return false
        say(R.string.calibrate_first)
        startCalibration()
        return true
    }

    fun startCalibration() {
        _state.update {
            if (it.image == null) it else it.copy(calibrating = CalibrationEdit.from(it.wall.calibration), selected = null)
        }
    }

    /** Corner [index] of the calibration under way moved to [to], kept on the photo. */
    fun moveCorner(index: Int, to: Pt) {
        _state.update { state ->
            val edit = state.calibrating ?: return@update state
            val clamped = Pt(to.x.coerceIn(0.0, 1.0), to.y.coerceIn(0.0, 1.0))
            state.copy(calibrating = edit.copy(corners = edit.corners.toMutableList().also { it[index] = clamped }))
        }
    }

    fun setWidthText(text: String) {
        _state.update { state -> state.copy(calibrating = state.calibrating?.copy(widthText = text)) }
    }

    fun setHeightText(text: String) {
        _state.update { state -> state.copy(calibrating = state.calibrating?.copy(heightText = text)) }
    }

    /** The calibration under way onto the wall, when it makes one. */
    fun applyCalibration() {
        commit { state ->
            val result = state.calibrating?.result ?: return@commit state
            state.copy(wall = state.wall.copy(calibration = result), calibrating = null)
        }
    }

    /** The calibration under way dropped; the wall keeps the one it had, if any. */
    fun cancelCalibration() {
        _state.update { it.copy(calibrating = null) }
    }

    private fun updateSelected(change: (Picture) -> Picture) {
        commit { state ->
            val id = state.selected ?: return@commit state
            state.copy(wall = state.wall.updated(id, change))
        }
    }

    /**
     * [transform] applied to the state, the wall before it a step back if it
     * changed the wall. On the main thread only, as every caller is: the
     * step is recorded beside the change, not inside an update that may run
     * twice.
     */
    private fun commit(transform: (EditorState) -> EditorState) {
        val old = _state.value
        val next = transform(old)
        if (next.wall != old.wall) history.record(old.wall)
        _state.value = pruned(withHistory(next))
    }

    private fun withHistory(state: EditorState) = state.copy(canUndo = history.canUndo, canRedo = history.canRedo)

    /** [state] holding the photos its wall shows and no others: a step back to the others reads them again. */
    private fun pruned(state: EditorState): EditorState {
        val names = state.wall.photoNames
        return if (state.photos.keys.all { it in names }) state else state.copy(photos = state.photos.filterKeys { it in names })
    }

    /** The photos the wall shows but the state does not hold yet — after a step back or forward — read in. */
    private fun loadMissingPhotos() {
        val state = _state.value
        val missing = state.wall.photoNames - state.photos.keys
        if (missing.isEmpty()) return
        val context = getApplication<Application>()
        val wallPhoto = state.wall.photo
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                missing.mapNotNull { name -> Photos.loadShown(context, name, wall = name == wallPhoto)?.let { name to it.asImageBitmap() } }
            }
            // Only those the wall still shows: it may have moved on meanwhile.
            _state.update { now -> now.copy(photos = now.photos + loaded.filter { it.first in now.wall.photoNames }) }
        }
    }

    /**
     * [read] a photo in off the main thread, then [apply] it to the state as it
     * is by then; a photo that cannot be read is said so, and changes nothing.
     * One at a time: the editor is busy meanwhile.
     */
    private fun <T> importing(read: () -> T, apply: (EditorState, T) -> EditorState) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Throwable, not Exception: a photo too large for memory is just as unreadable.
                runCatching(read).getOrNull()
            }
            if (result == null) {
                _state.update { it.copy(busy = false) }
                say(R.string.photo_unreadable)
            } else {
                commit { apply(it, result).copy(busy = false) }
            }
        }
    }

    private fun say(@StringRes text: Int) {
        _notices.trySend(text)
    }
}

/** Every photo of [wall] as shown, by name; one that cannot be read is left out, and drawn as missing. */
private fun loadPhotos(context: Application, wall: Wall): Map<String, ImageBitmap> =
    wall.photoNames.mapNotNull { name ->
        Photos.loadShown(context, name, wall = name == wall.photo)?.let { name to it.asImageBitmap() }
    }.toMap()

/**
 * Where the middle of the photo is on the wall, in centimetres; the middle of
 * the calibration's rectangle when the photo's is past the wall's horizon.
 */
private fun photoCentreOnWall(calibration: Calibration): Pt =
    calibration.wallToPhoto()?.inverse()?.map(Pt(0.5, 0.5))
        ?: Pt(calibration.widthCm / 2, calibration.heightCm / 2)
