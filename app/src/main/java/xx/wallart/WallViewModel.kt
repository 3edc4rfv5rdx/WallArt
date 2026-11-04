package xx.wallart

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Everything the editor shows. [image] is the wall's photo as shown; [calibrating] the calibration under way, if one is. */
data class EditorState(
    val wall: Wall = Wall(),
    val image: ImageBitmap? = null,
    val calibrating: CalibrationEdit? = null,
    val busy: Boolean = false,
)

class WallViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** One-off lines for the user, each said once. */
    private val _notices = Channel<Int>(Channel.BUFFERED)
    val notices = _notices.receiveAsFlow()

    /**
     * [uri] as the wall's photo. A new photo is a new view of the wall, so the
     * old calibration says nothing about it: the calibration starts over, from
     * the size the last one had.
     */
    fun importPhoto(uri: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                // Throwable, not Exception: a photo too large for memory is just as unreadable.
                runCatching { WallPhoto.import(getApplication(), uri) }.getOrNull()
            }
            if (bitmap == null) {
                _state.update { it.copy(busy = false) }
                say(R.string.photo_unreadable)
                return@launch
            }
            _state.update { state ->
                val old = state.wall.calibration
                state.copy(
                    wall = Wall(calibration = null),
                    image = bitmap.asImageBitmap(),
                    calibrating = CalibrationEdit.from(old).copy(corners = INITIAL_CORNERS),
                    busy = false,
                )
            }
        }
    }

    fun startCalibration() {
        _state.update { if (it.image == null) it else it.copy(calibrating = CalibrationEdit.from(it.wall.calibration)) }
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
        _state.update { state ->
            val result = state.calibrating?.result ?: return@update state
            state.copy(wall = state.wall.copy(calibration = result), calibrating = null)
        }
    }

    /** The calibration under way dropped; the wall keeps the one it had, if any. */
    fun cancelCalibration() {
        _state.update { it.copy(calibrating = null) }
    }

    private fun say(@StringRes text: Int) {
        _notices.trySend(text)
    }
}
