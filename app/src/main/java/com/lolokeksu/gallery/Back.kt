package com.lolokeksu.gallery

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

/**
 * The Android 14 back gesture, which this application was not taking part in: the manifest had
 * never opted in, so a swipe from the edge gave no sign of where it led until it was over.
 *
 * Returns how far the gesture has travelled, 0 to 1. Letting go past the system's own threshold
 * calls [onBack]; letting go early cancels and the progress returns to zero, so the screen has to
 * follow the finger rather than jump.
 *
 * Only for leaving a screen. A back press that takes a step inside one — dropping a selection,
 * closing a file, bringing the chrome back — stays an ordinary BackHandler, because a predictive
 * animation there would promise an exit that does not happen.
 */
@Composable
internal fun predictiveBackProgress(enabled: Boolean, onBack: () -> Unit): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event -> progress = event.progress }
            progress = 0f
            onBack()
        } catch (_: CancellationException) {
            progress = 0f
        }
    }
    return progress
}

/** What the system does to an activity being swiped away: it shrinks and fades a little. */
internal fun Modifier.predictiveBack(progress: Float): Modifier = graphicsLayer {
    val shrink = 1f - progress * .14f
    scaleX = shrink
    scaleY = shrink
    alpha = 1f - progress * .25f
}
