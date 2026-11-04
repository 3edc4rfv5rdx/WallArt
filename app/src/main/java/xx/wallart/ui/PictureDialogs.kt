package xx.wallart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xx.wallart.ArtSize
import xx.wallart.ArtSpec
import xx.wallart.STANDARD_ART_SIZES
import xx.wallart.R
import xx.wallart.formatCm
import xx.wallart.isArtSize
import xx.wallart.standardSizeOf
import xx.wallart.parseCm

/** The frame colours offered: woods from near black to pale, metals, and a few paints. */
private val FRAME_COLORS = listOf(
    0xFF1A1A1A, 0xFF2B2622, 0xFF5C3A21, 0xFF8C6239,
    0xFFA0703F, 0xFFD8B98A, 0xFFF5F5F0, 0xFF8A8A8A,
    0xFFC0C0C0, 0xFFC9A23F, 0xFF1F2E4A, 0xFF6B2A22,
).map { it.toInt() }

/** How far a tap on − or + moves an artwork's side, and the frame's width, in centimetres. */
private const val SIDE_STEP = 1.0
private const val MOULDING_STEP = 0.5

/**
 * Under [title], a picture's artwork size and frame width in centimetres, starting at
 * [initial] — each typed or stepped by − and + — its way round, and the
 * standard sizes to pick from; the size it comes to outside the frame large
 * in the title. [buttons] are given the size as it stands, null while it
 * makes no picture.
 */
@Composable
fun PictureSizeDialog(title: String, initial: ArtSpec, onDismiss: () -> Unit, buttons: @Composable (ArtSpec?) -> Unit) {
    var width by rememberSaveable { mutableStateOf(formatCm(initial.widthCm)) }
    var height by rememberSaveable { mutableStateOf(formatCm(initial.heightCm)) }
    // Zero is a frame too: an artwork hung bare.
    var moulding by rememberSaveable { mutableStateOf(formatCm(initial.mouldingCm)) }
    val w = parseCm(width)
    val h = parseCm(height)
    val m = parseCm(moulding, allowZero = true)
    val size = if (w != null && h != null && m != null && isArtSize(w, h, m)) ArtSpec(w, h, m) else null
    // The way round is what the sides typed make it; while one is not a number, what it started as.
    val landscape = if (w != null && h != null) w > h else initial.widthCm > initial.heightCm
    val cm = stringResource(R.string.cm)
    val portraitLabel = stringResource(R.string.portrait)
    val landscapeLabel = stringResource(R.string.landscape)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                // The size outside the frame: what the wall gets.
                val outside = size?.let { (a, b, c) -> "${formatCm(a + 2 * c)}×${formatCm(b + 2 * c)} $cm" } ?: NO_VALUE
                Text(outside, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownRow(
                    label = stringResource(R.string.orientation),
                    value = if (landscape) landscapeLabel else portraitLabel,
                    options = listOf(false, true),
                    optionLabel = { if (it) landscapeLabel else portraitLabel },
                ) { wanted ->
                    // The other way round is the same sides swapped.
                    if (wanted != landscape) width = height.also { height = width }
                }
                DropdownRow(
                    label = stringResource(R.string.standard_size),
                    value = if (w != null && h != null) standardSizeOf(w, h)?.let(::sizeLabel) ?: NO_VALUE else NO_VALUE,
                    options = STANDARD_ART_SIZES,
                    optionLabel = ::sizeLabel,
                ) { standard ->
                    width = formatCm(if (landscape) standard.long else standard.short)
                    height = formatCm(if (landscape) standard.short else standard.long)
                }
                CmField("${stringResource(R.string.art_width)}, $cm", width, SIDE_STEP, allowZero = false) { width = it }
                CmField("${stringResource(R.string.art_height)}, $cm", height, SIDE_STEP, allowZero = false) { height = it }
                CmField("${stringResource(R.string.frame_width)}, $cm", moulding, MOULDING_STEP, allowZero = true) { moulding = it }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
        confirmButton = { buttons(size) },
    )
}

/** A standard size as the list shows it: short side by long, and its name after it if it has one. */
private fun sizeLabel(size: ArtSize): String {
    val sides = "${formatCm(size.short)}×${formatCm(size.long)}"
    return size.name?.let { "$sides $it" } ?: sides
}

/**
 * A length in centimetres, typed or moved [step] at a time by − and +: never
 * below a step — or below nothing, when [allowZero] — and to a tenth, so
 * steps from a typed fraction stay readable.
 */
@Composable
private fun CmField(label: String, value: String, step: Double, allowZero: Boolean, onChange: (String) -> Unit) {
    val floor = if (allowZero) 0.0 else step
    fun stepped(by: Double): String {
        val now = parseCm(value, allowZero = true) ?: floor
        return formatCm((Math.round((now + by) * 10) / 10.0).coerceAtLeast(floor))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(stepped(-step)) }) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.less)) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            // A size is read at a glance, the figures larger than the label.
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(stepped(step)) }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.more)) }
    }
}

/** The frame colours as swatches, [current] ringed; a tap picks one. */
@Composable
fun FrameColorDialog(current: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.frame_color)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FRAME_COLORS.forEach { color ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Color(color), shape)
                            .border(
                                width = if (color == current) 3.dp else 1.dp,
                                color = if (color == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = shape,
                            )
                            .clickable { onPick(color) },
                    )
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** A picture's size kept across the activity being made again, side by side. */
val ArtSpecSaver = Saver<ArtSpec, List<Double>>(
    save = { listOf(it.widthCm, it.heightCm, it.mouldingCm) },
    restore = { ArtSpec(it[0], it[1], it[2]) },
)
