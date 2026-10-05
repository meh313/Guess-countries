package com.example.support

import com.example.ui.viewmodel.CountryViewModel
import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.robolectric.Shadows.shadowOf

/** Taps a bottom-navigation tab of the real activity. [route] is "explore", "flashcards", "quiz" or "stats". */
fun ComposeTestRule.openTab(route: String) {
    onNodeWithTag("nav_tab_$route").performClick()
    waitForIdle()
}

/** Where the tagged node sits in the window, in pixels. Reads the unmerged tree so inner parts can be tagged. */
fun ComposeTestRule.boundsOf(tag: String): Rect =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

/** Room and flows deliver asynchronously; pumps the main looper until [check] stops throwing. */
fun ComposeTestRule.eventually(what: String, timeoutMillis: Long = 10_000, check: () -> Unit) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        waitForIdle()
        try {
            check()
            return
        } catch (e: AssertionError) {
            last = e
            Thread.sleep(25)
        }
    }
    throw AssertionError("Timed out waiting for $what", last)
}

/** Matches a node whose TalkBack state text is exactly [expected]. */
fun stateDescriptionIs(expected: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

/** WCAG contrast ratio between two colours, from 1 (identical) to 21 (black on white). */
fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** Waits until the weak spots number [count], handing Room's results to the main thread while it waits. */
fun ComposeTestRule.awaitWeakSpots(viewModel: CountryViewModel, count: Int, timeoutMillis: Long = 10_000) =
    awaitWeakSpotsWhere(viewModel, timeoutMillis) { it == count }

/** Waits until the number of weak spots satisfies [accept], handing Room's results to the main thread meanwhile. */
fun ComposeTestRule.awaitWeakSpotsWhere(viewModel: CountryViewModel, timeoutMillis: Long = 10_000, accept: (Int) -> Boolean) =
    waitUntil(timeoutMillis) {
        shadowOf(Looper.getMainLooper()).idle()
        accept(viewModel.weakSpots.value.size)
    }
