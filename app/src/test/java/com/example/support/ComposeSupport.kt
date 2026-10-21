package com.example.support

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A node of the quiz setup screen, scrolled into view. The scope and difficulty chips sit in rows that scroll
 * sideways inside the column that scrolls down above the pinned Start button; `performScrollTo` moves only the
 * nearest scrollable (the row), so the column is scrolled first, by the distance the node is outside its bounds.
 */
fun ComposeTestRule.setupNode(tag: String): SemanticsNodeInteraction {
    val column = onNodeWithTag("quiz_setup_scroll")
    repeat(5) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val visible = column.fetchSemanticsNode().boundsInRoot
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        val delta = when {
            bottom > visible.bottom -> bottom - visible.bottom + 24f
            top < visible.top -> top - visible.top - 24f
            else -> 0f
        }
        if (delta == 0f) return onNodeWithTag(tag).performScrollTo()
        column.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        waitForIdle()
    }
    return onNodeWithTag(tag).performScrollTo()
}

/**
 * A lifecycle the test moves by hand, to stand in for the activity's under a screen (provide it as
 * `LocalLifecycleOwner`): [stop] is what pressing Home or locking the phone does, with the screen still composed.
 * Resumed to begin with; move it on the UI thread.
 */
class HandMovedLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry

    /** ON_PAUSE and ON_STOP: the activity is no longer visible. */
    fun stop() {
        registry.currentState = Lifecycle.State.CREATED
    }

    /** ON_START and ON_RESUME: the activity is in front again. */
    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
