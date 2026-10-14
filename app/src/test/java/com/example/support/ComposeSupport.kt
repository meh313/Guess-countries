package com.example.support

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction

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
