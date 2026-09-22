// The whole file is about one experimental API, and the composition local's own type is part
// of it, so the opt-in belongs to the file rather than to each declaration.
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lolokeksu.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The transform that carries a tile into the viewer and back.
 *
 * Opening a photograph used to swap one screen for another behind a cross-fade, which said nothing
 * about where the picture came from. Now the tile grows into the page and shrinks back into
 * whichever tile the viewer was left on.
 *
 * The scope travels through a composition local for the same reason the palette does: the two
 * sides sit four levels apart, and threading a parameter through every screen between them would
 * put transition plumbing in signatures that have nothing to do with it.
 */
internal val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The grid side. The tile is not inside the AnimatedVisibility that shows the viewer, so its
 * visibility is stated rather than derived: it hides while that photograph is the one on screen,
 * and reappearing is what gives the closing animation somewhere to land.
 */
@Composable
internal fun Modifier.sharedTile(key: String, hidden: Boolean): Modifier {
    val scope = LocalSharedTransition.current
    return if (scope == null) this else with(scope) {
        this@sharedTile.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key = key), visible = !hidden
        )
    }
}

/**
 * The viewer side, on the page that is actually on screen. [visibility] comes from the
 * AnimatedVisibility that holds the viewer, so the transform runs on the way in and on the way out.
 */
@Composable
internal fun Modifier.sharedMedia(key: String, visibility: AnimatedVisibilityScope?): Modifier {
    val scope = LocalSharedTransition.current
    return if (scope == null || visibility == null) this else with(scope) {
        this@sharedMedia.sharedElement(
            rememberSharedContentState(key = key), animatedVisibilityScope = visibility
        )
    }
}
