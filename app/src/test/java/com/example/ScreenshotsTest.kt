package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.example.support.FreshDatabaseRule
import com.example.support.drawWindow
import com.example.support.openTab
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Saves a picture of each screen under app/build/screenshots so a change can be looked at without a
 * device (CI uploads the folder). Not an assertion suite: a screen that fails to render fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotsTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private fun save(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.drawWindow().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun explore_flashcards_quiz_and_stats() {
        rule.waitForIdle()
        save("explore")
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_za"))
        save("explore_scrolled")

        rule.openTab("flashcards")
        save("flashcards_front")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        save("flashcards_back")

        rule.openTab("quiz")
        save("quiz_setup")

        rule.openTab("stats")
        save("stats")
    }
}
