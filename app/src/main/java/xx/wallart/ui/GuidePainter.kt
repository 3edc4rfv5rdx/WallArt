package xx.wallart.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.wallart.Guide
import xx.wallart.Homography
import xx.wallart.formatCm
import kotlin.math.roundToLong

/**
 * Draws the guides of a snap through the wall's projection: a line in the
 * accent over a dark one, so it shows on any wall, and on a gap's guide its
 * size in centimetres in a pill at its middle.
 */
internal class GuidePainter {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pill = RectF()

    fun draw(scope: DrawScope, guides: List<Guide>, box: ImageBox, homography: Homography, accent: Color, cm: String) {
        if (guides.isEmpty()) return
        val line = with(scope) { 1.5.dp.toPx() }
        val padding = with(scope) { 4.dp.toPx() }
        textPaint.textSize = with(scope) { 13.sp.toPx() }
        textPaint.color = Color.White.toArgb()
        scope.drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            for (guide in guides) {
                val from = homography.map(guide.from)?.let(box::toScreen) ?: continue
                val to = homography.map(guide.to)?.let(box::toScreen) ?: continue
                linePaint.color = Color.Black.copy(alpha = 0.5f).toArgb()
                linePaint.strokeWidth = line * 2
                canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
                linePaint.color = accent.toArgb()
                linePaint.strokeWidth = line
                canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
                val gap = guide.gapCm ?: continue
                // To a tenth of a centimetre: a finer figure only says how unsteady the finger was.
                val text = "${formatCm((gap * 10).roundToLong() / 10.0)} $cm"
                val mid = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
                val half = textPaint.measureText(text) / 2 + padding
                val top = mid.y - textPaint.textSize / 2 - padding
                val bottom = mid.y + textPaint.textSize / 2 + padding
                pill.set(mid.x - half, top, mid.x + half, bottom)
                pillPaint.color = accent.toArgb()
                canvas.drawRoundRect(pill, (bottom - top) / 2, (bottom - top) / 2, pillPaint)
                // The baseline put so the text's middle is the pill's.
                val baseline = mid.y - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(text, mid.x, baseline, textPaint)
            }
        }
    }
}
