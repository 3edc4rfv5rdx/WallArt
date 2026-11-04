package xx.wallart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** What a field shows when there is nothing to show: no preset the format makes. */
private const val NO_VALUE = "—"

/** Every drop-down field the same width, wide enough for the longest preset's name. */
private val DROPDOWN_WIDTH = 176.dp

/**
 * The format the image is saved in, for a photo [photoW] × [photoH]: its way
 * round, shape, size and preset, as PhotoCollage's. A new shape or way round
 * takes the size nearest what its crop of the photo really is. Done applies
 * it as one step; Cancel leaves the format as it was.
 */
@Composable
fun FormatDialog(format: Format, photoW: Int, photoH: Int, onDismiss: () -> Unit, onDone: (Format) -> Unit) {
    var edit by rememberSaveable(stateSaver = FormatSaver) { mutableStateOf(format) }
    val photoRatio = photoW.toDouble() / photoH
    val portrait = stringResource(R.string.portrait)
    val landscape = stringResource(R.string.landscape)
    val original = stringResource(R.string.original)
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
                ) { edit = edit.copy(landscape = it).fitted(photoW, photoH) }
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
                    value = edit.preset()?.label ?: NO_VALUE,
                    options = Preset.entries,
                    optionLabel = { it.label },
                ) { edit = edit.withPreset(it) }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
        confirmButton = { Button(onClick = { onDone(edit) }) { Text(stringResource(R.string.done)) } },
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

private fun sizeText(size: Pair<Int, Int>): String = "${size.first}×${size.second}"

/** A format kept across the activity being made again, field by field. */
private val FormatSaver = Saver<Format, List<Any>>(
    save = { listOf(it.aspect.name, it.landscape, it.longSide, it.cropX, it.cropY) },
    restore = {
        Format(Aspect.valueOf(it[0] as String), it[1] as Boolean, it[2] as Int, it[3] as Double, it[4] as Double)
    },
)

/** A setting's [label], and its [value] in a field that opens the [options] to pick from; still while not [enabled]. */
@Composable
private fun <T> DropdownRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.width(DROPDOWN_WIDTH)) {
                Text(value, maxLines = 1)
            }
            AppMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    AppMenuItem(optionLabel(option)) {
                        open = false
                        onPick(option)
                    }
                }
            }
        }
    }
}
