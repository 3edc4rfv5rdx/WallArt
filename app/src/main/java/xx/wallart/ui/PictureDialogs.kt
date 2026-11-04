package xx.wallart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xx.wallart.Picture
import xx.wallart.R
import xx.wallart.formatCm
import xx.wallart.isPictureSize
import xx.wallart.parseCm

/** The frame colours offered: woods from near black to pale, metals, and a few paints. */
private val FRAME_COLORS = listOf(
    0xFF1A1A1A, 0xFF2B2622, 0xFF5C3A21, 0xFF8C6239,
    0xFFA0703F, 0xFFD8B98A, 0xFFF5F5F0, 0xFF8A8A8A,
    0xFFC0C0C0, 0xFFC9A23F, 0xFF1F2E4A, 0xFF6B2A22,
).map { it.toInt() }

/** The picture's outer size and frame width in centimetres; Done only while they make a picture. */
@Composable
fun PictureSizeDialog(picture: Picture, onDismiss: () -> Unit, onDone: (Double, Double, Double) -> Unit) {
    var width by rememberSaveable { mutableStateOf(formatCm(picture.bounds.width)) }
    var height by rememberSaveable { mutableStateOf(formatCm(picture.bounds.height)) }
    // Zero is a frame too: an artwork hung bare.
    var moulding by rememberSaveable { mutableStateOf(formatCm(picture.mouldingCm)) }
    val w = parseCm(width)
    val h = parseCm(height)
    val m = parseCm(moulding, allowZero = true)
    val size = if (w != null && h != null && m != null && isPictureSize(w, h, m)) Triple(w, h, m) else null
    val cm = stringResource(R.string.cm)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CmField("${stringResource(R.string.width)}, $cm", width) { width = it }
                CmField("${stringResource(R.string.height)}, $cm", height) { height = it }
                CmField("${stringResource(R.string.frame_width)}, $cm", moulding) { moulding = it }
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
        confirmButton = {
            Button(onClick = { size?.let { (a, b, c) -> onDone(a, b, c) } }, enabled = size != null) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun CmField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
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
