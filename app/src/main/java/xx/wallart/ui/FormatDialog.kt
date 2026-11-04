package xx.wallart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xx.wallart.Aspect
import xx.wallart.Format
import xx.wallart.Preset
import xx.wallart.R
import xx.wallart.ratioLabel

/**
 * The format the image is saved in, for a photo [photoW] × [photoH]: its way
 * round, shape, size and preset, as PhotoCollage's. A new shape takes the
 * size nearest what its crop of the photo really is; a new way round keeps
 * it. Done applies it as one step; Cancel leaves the format as it was.
 */
@Composable
fun FormatDialog(format: Format, photoW: Int, photoH: Int, onDismiss: () -> Unit, onDone: (Format) -> Unit) {
    var edit by rememberSaveable(stateSaver = FormatSaver) { mutableStateOf(format) }
    val photoRatio = photoW.toDouble() / photoH
    val portrait = stringResource(R.string.portrait)
    val landscape = stringResource(R.string.landscape)
    // The photo's own shape named with it, so the original says what it is.
    val original = "${stringResource(R.string.original)} ${ratioLabel(photoW, photoH)}"
    // The original shape is the photo's own way round, and offers no other.
    val isLandscape = if (edit.aspect == Aspect.ORIGINAL) photoW > photoH else edit.landscape
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownRow(
                    label = stringResource(R.string.orientation),
                    value = if (isLandscape) landscape else portrait,
                    options = listOf(false, true),
                    optionLabel = { if (it) landscape else portrait },
                    enabled = edit.aspect != Aspect.ORIGINAL && edit.aspect != Aspect.SQUARE,
                ) {
                    // The size stays: only a new shape takes the one nearest its crop.
                    edit = edit.copy(landscape = it)
                }
                DropdownRow(
                    label = stringResource(R.string.shape),
                    value = edit.aspect.label(isLandscape, original),
                    options = Aspect.entries,
                    optionLabel = { it.label(isLandscape, original) },
                ) { aspect ->
                    // Into a shape from the original, the way round the photo is.
                    val way = if (edit.aspect == Aspect.ORIGINAL) photoW > photoH else edit.landscape
                    edit = edit.copy(aspect = aspect, landscape = way).fitted(photoW, photoH)
                }
                DropdownRow(
                    label = stringResource(R.string.size),
                    value = sizeText(edit.savedSize(photoRatio)),
                    options = edit.aspect.sizes,
                    optionLabel = { sizeText(edit.savedSize(photoRatio, it)) },
                ) { edit = edit.copy(longSide = it) }
                // The preset sets the three above: set apart from them.
                HorizontalDivider(
                    thickness = 3.dp,
                    modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally),
                )
                DropdownRow(
                    label = stringResource(R.string.preset),
                    value = edit.preset()?.label(isLandscape, original) ?: NO_VALUE,
                    options = Preset.entries,
                    optionLabel = { it.label(isLandscape, original) },
                ) { edit = edit.withPreset(it, isLandscape) }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
        confirmButton = { DialogButton(stringResource(R.string.done)) { onDone(edit) } },
    )
}

/** The shape as its sides read the [landscape] way round; [original] names the photo's own. */
private fun Aspect.label(landscape: Boolean, original: String): String {
    val (short, long) = when (this) {
        Aspect.ORIGINAL -> return original
        Aspect.SQUARE -> return "1:1"
        Aspect.A4 -> return "A4"
        Aspect.FOUR_FIVE -> "4" to "5"
        Aspect.THREE_FOUR -> "3" to "4"
        Aspect.TWO_THREE -> "2" to "3"
        Aspect.NINE_SIXTEEN -> "9" to "16"
    }
    return if (landscape) "$long:$short" else "$short:$long"
}

/** The preset's name, and its shape the [landscape] way round when that is what tells it apart. */
private fun Preset.label(landscape: Boolean, original: String): String =
    if (shaped) "$label ${aspect.label(landscape, original)}" else label

private fun sizeText(size: Pair<Int, Int>): String = "${size.first}×${size.second}"

/** A format kept across the activity being made again, field by field. */
private val FormatSaver = Saver<Format, List<Any>>(
    save = { listOf(it.aspect.name, it.landscape, it.longSide, it.cropX, it.cropY) },
    restore = {
        Format(Aspect.valueOf(it[0] as String), it[1] as Boolean, it[2] as Int, it[3] as Double, it[4] as Double)
    },
)
