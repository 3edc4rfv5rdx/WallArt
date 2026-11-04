package xx.wallart.ui

import android.view.OrientationEventListener
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/** How far past a quarter's edge the phone must tilt before the wall turns. */
private const val DEAD_ZONE_DEGREES = 20

/**
 * The phone's tilt in quarter turns clockwise (0, 90, 180, 270), straight off
 * the accelerometer: the activity is held portrait, so a wall photo held
 * sideways turns by this to fill the screen. A dead zone round each quarter's
 * edge keeps it from flickering between two while the phone sits near one.
 */
@Composable
fun rememberDeviceRotation(): Int {
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // The tilt the current turn stands for: a wall turned 270 is a phone at 90.
                val centre = (360 - rotation) % 360
                val off = abs(orientation - centre).let { minOf(it, 360 - it) }
                if (off <= 45 + DEAD_ZONE_DEGREES) return
                rotation = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation < 135 -> 270
                    orientation < 225 -> 180
                    else -> 90
                }
            }
        }
        // Without a working accelerometer, as on an emulator, it fires with meaningless values: trusted only once it
        // says it can tell.
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}

/**
 * [content] laid out in the space [modifier] gives it, turned by [rotation]:
 * at 90 and 270 with the sides swapped, so once turned it still fills it.
 * Touches reach it in its own turned coordinates.
 */
@Composable
fun RotatedFrame(rotation: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val turned = rotation == 90 || rotation == 270
        val frame = if (turned) Modifier.requiredSize(width = maxHeight, height = maxWidth) else Modifier.fillMaxSize()
        Box(frame.graphicsLayer { rotationZ = rotation.toFloat() }, contentAlignment = Alignment.Center, content = content)
    }
}
