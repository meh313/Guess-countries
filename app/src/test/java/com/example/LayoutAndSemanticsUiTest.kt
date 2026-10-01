package com.example

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Layout at several window sizes and the accessibility semantics of toggles and selections. */
@RunWith(RobolectricTestRunner::class)
class LayoutAndSemanticsUiTest {

  @get:Rule val rule = createAndroidComposeRule<MainActivity>()

  private val density get() = rule.density.density

  private fun tab(route: String) {
    rule.onNodeWithTag("nav_tab_$route").performClick()
    rule.waitForIdle()
  }

  private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Room and flows deliver asynchronously; pump the main looper until [check] stops throwing. */
  private fun eventually(what: String, check: () -> Unit) {
    val deadline = System.currentTimeMillis() + 10_000
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      rule.waitForIdle()
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

  private fun stateDescription(expected: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

  // ---- Explore grid -------------------------------------------------------------------------

  @Test
  @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
  fun phone_exploreShowsTwoColumns() {
    val ar = bounds("country_card_ar")
    assertEquals("Antarctica and Argentina share a row", bounds("country_card_aq").top, ar.top, 1f)
    assertTrue("Australia starts the next row", bounds("country_card_au").top > ar.top)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w840dp-h1180dp-xhdpi")
  fun tablet_exploreUsesMoreThanTwoColumns() {
    val firstRowTop = bounds("country_card_aq").top
    val inFirstRow = listOf("aq", "ar", "au", "br").count { bounds("country_card_$it").top == firstRowTop }
    assertEquals(4, inFirstRow)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
  fun landscapePhone_countriesAreReachableByScrolling() {
    // Used to be impossible: a fixed header filled the whole 360dp-tall window and the grid had no height.
    rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
    rule.onNodeWithTag("country_card_fr").assertIsDisplayed()
  }

  // ---- Flashcards in landscape ----------------------------------------------------------------

  @Test
  @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
  fun landscapePhone_flashcardShowsChipFlagAndHintInsideTheCard() {
    tab("flashcards")

    val card = bounds("flashcard_flip_card")
    val flag = bounds("flashcard_flag")
    assertTrue("flag inside card: $flag in $card", flag.top >= card.top && flag.bottom <= card.bottom)
    assertEquals("flag keeps its 3:2 shape", 1.5f, flag.width / flag.height, 0.05f)
    assertTrue("flag is at least 72dp tall but at most 200dp", flag.height / density in 72f..200.5f)

    // The hint pill must sit below the flag rather than on top of it, and the grade buttons stay on screen.
    rule.onNodeWithTag("grade_hard_btn").assertIsDisplayed()
    rule.onNodeWithTag("grade_mastered_btn").assertIsDisplayed()
    rule.onNodeWithTag("flashcard_flip_card").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
  fun landscapePhone_gradeButtonsStillAdvanceTheDeck() {
    tab("flashcards")

    rule.onNodeWithTag("grade_mastered_btn").performClick()
    rule.waitForIdle()

    rule.onNodeWithTag("flashcard_flip_card").assertIsDisplayed()
    val card = bounds("flashcard_flip_card")
    val flag = bounds("flashcard_flag")
    assertTrue("the next card's flag is inside the card: $flag in $card", flag.top >= card.top && flag.bottom <= card.bottom)
  }

  @Test
  @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
  fun portraitPhone_flashcardFlagIsAtMost200dpTall() {
    tab("flashcards")

    val flag = bounds("flashcard_flag")
    assertTrue(flag.height / density <= 200.5f)
    assertEquals(1.5f, flag.width / flag.height, 0.05f)
  }

  // ---- Accessibility semantics ----------------------------------------------------------------

  @Test
  @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
  fun bookmarkButton_isAToggleThatReflectsItsState() {
    val button = rule.onNodeWithTag("favorite_btn_ar")
    button.assertIsToggleable().assertIsOff()

    button.performClick()

    eventually("the bookmark to be saved") { rule.onNodeWithTag("favorite_btn_ar").assertIsOn() }
  }

  @Test
  @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
  fun continentChips_exposeWhichOneIsSelected() {
    rule.onNodeWithTag("continent_chip_all").assertIsSelected()
    rule.onNodeWithTag("continent_chip_europe").assertIsNotSelected()

    rule.onNodeWithTag("continent_chip_europe").performClick()
    rule.waitForIdle()

    rule.onNodeWithTag("continent_chip_europe").assertIsSelected()
    rule.onNodeWithTag("continent_chip_all").assertIsNotSelected()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
  fun flipCard_announcesWhichSideIsShowing() {
    tab("flashcards")
    val card: SemanticsNodeInteraction = rule.onNodeWithTag("flashcard_flip_card")
    card.assert(stateDescription("Showing the flag"))

    card.performClick()
    rule.waitForIdle()

    rule.onNodeWithTag("flashcard_flip_card").assert(stateDescription("Showing country details"))
  }
}
