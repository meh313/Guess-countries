package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.lifecycle.ViewModelProvider
import com.example.quiz.allCountries
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.example.data.model.CountryRepository
import com.example.quiz.byName
import com.example.support.FreshDatabaseRule
import com.example.support.boundsOf
import com.example.support.contrastRatio
import com.example.support.crop
import com.example.support.drawWindow
import com.example.support.eventually
import com.example.support.openTab
import com.example.support.stateDescriptionIs
import com.example.ui.components.FlagAspectRatio
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout at several window sizes and the accessibility semantics of toggles and selections.
 *
 * Native graphics give real font metrics. The default mode measures text with approximate widths, which
 * hides bugs that depend on how wide a string is (a long title squeezing the buttons beside it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutAndSemanticsUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // The app database is a singleton, so reset it before the activity starts to keep tests independent.
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private val density get() = rule.density.density

    // ---- Explore grid -------------------------------------------------------------------------

    /** Card tags in the Explore tab's default order (by name), straight from the catalog. */
    private val cardsByName get() = byName.map { "country_card_${it.code.lowercase()}" }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun phone_exploreShowsTwoColumns() {
        val (first, second, third) = cardsByName.take(3).map { rule.boundsOf(it) }
        assertEquals("the first two countries share a row", first.top, second.top, 1f)
        assertTrue("they sit side by side", first.right <= second.left + 1f)
        assertTrue("the third starts the next row", third.top > first.top)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h1180dp-xhdpi")
    fun tablet_exploreUsesMoreThanTwoColumns() {
        val firstRowTop = rule.boundsOf(cardsByName.first()).top
        val inFirstRow = cardsByName.take(5).count { rule.boundsOf(it).top == firstRowTop }
        assertTrue("only $inFirstRow cards in the first row", inFirstRow >= 3)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
    fun landscapePhone_countriesAreReachableByScrolling() {
        // Used to be impossible: a fixed header filled the whole 360dp-tall window and the grid had no height.
        val last = cardsByName.last()
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(last))
        rule.onNodeWithTag(last).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun searchField_keepsFocusWhileTheListFiltersAsYouType() {
        // The field now lives inside the scrolling grid; the grid below it changes on every keystroke.
        val shown = ViewModelProvider(rule.activity)[CountryViewModel::class.java].filteredCountries
        rule.onNodeWithTag("search_country_input").performClick()
        rule.onNodeWithTag("search_country_input").performTextInput("fr")
        rule.eventually("the list to filter") { assertTrue(shown.value.size < allCountries.size) }
        val afterFr = shown.value.size
        assertTrue("\"fr\" should match more than one country, or this test proves nothing", afterFr > 1)
        rule.onNodeWithTag("search_country_input").assertIsFocused()

        rule.onNodeWithTag("search_country_input").performTextInput("a")
        rule.eventually("the list to narrow again") { assertTrue(shown.value.size < afterFr) }

        rule.onNodeWithTag("search_country_input").assertIsFocused()
        rule.onNodeWithTag("search_country_input").assert(hasText("fra"))
    }

    // ---- Flashcards in landscape ----------------------------------------------------------------

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
    fun landscapePhone_flashcardShowsChipFlagAndHintInsideTheCardInOrder() {
        rule.openTab("flashcards")

        assertFrontFaceFits(minFlagDp = 72f)
        rule.onNodeWithTag("grade_hard_btn").assertIsDisplayed()
        rule.onNodeWithTag("grade_mastered_btn").assertIsDisplayed()
    }

    /** Chip above flag above hint, all inside the card, and the flag is a 4:3 rectangle of sensible size. */
    private fun assertFrontFaceFits(minFlagDp: Float) {
        val card = rule.boundsOf("flashcard_flip_card")
        val chip = rule.boundsOf("flashcard_continent_chip")
        val flag = rule.boundsOf("flashcard_flag")
        val hint = rule.boundsOf("flashcard_hint")
        val where = "card=$card chip=$chip flag=$flag hint=$hint"

        assertTrue("chip inside card: $where", chip.top >= card.top && chip.bottom <= card.bottom)
        assertTrue("chip is above the flag: $where", chip.bottom <= flag.top + 1f)
        assertTrue("hint is below the flag: $where", flag.bottom <= hint.top + 1f)
        assertTrue("hint inside card: $where", hint.bottom <= card.bottom + 1f)
        assertEquals("flag keeps its 4:3 shape: $where", FlagAspectRatio, flag.width / flag.height, 0.05f)
        assertTrue("flag at least ${minFlagDp}dp tall but at most 200dp: $where", flag.height / density in minFlagDp..200.5f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h480dp-xxhdpi")
    fun shortPortraitWindow_flagKeepsItsMinimumHeight() {
        // About 210dp for the card: the flag used to be squeezed to roughly 34dp (or vanish).
        rule.openTab("flashcards")

        val flag = rule.boundsOf("flashcard_flag")
        assertTrue("flag height ${flag.height / density}dp", flag.height / density >= 71.5f)
        rule.onNodeWithTag("flashcard_flip_card").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", fontScale = 1.5f)
    fun largeFonts_flagKeepsItsMinimumHeightAndTheCardStaysUsable() {
        rule.openTab("flashcards")

        val flag = rule.boundsOf("flashcard_flag")
        assertTrue("flag height ${flag.height / density}dp", flag.height / density >= 71.5f)
        rule.onNodeWithTag("grade_mastered_btn").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
    fun landscapePhone_deckHeaderActionsKeepTheirFullSize() {
        rule.openTab("flashcards")

        assertActionsAreTouchTargets()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi", fontScale = 1.5f)
    fun landscapePhone_deckHeaderActionsSurviveLargeFonts() {
        rule.openTab("flashcards")

        assertActionsAreTouchTargets()
    }

    private fun assertActionsAreTouchTargets() {
        for (label in listOf("Shuffle Cards", "Reset Deck")) {
            // Layout bounds, not touch bounds: Material extends the touch area to 48dp even when the button
            // itself has been squeezed to nothing. A normal icon button is 40dp.
            val r = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue("$label is ${r.width / density}dp wide", r.width / density >= 39.5f)
            assertTrue("$label is ${r.height / density}dp tall", r.height / density >= 39.5f)
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h360dp-xxhdpi")
    fun landscapePhone_gradeButtonsStillAdvanceTheDeck() {
        rule.openTab("flashcards")

        rule.onNodeWithTag("grade_mastered_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_flip_card").assertIsDisplayed()
        val card = rule.boundsOf("flashcard_flip_card")
        val flag = rule.boundsOf("flashcard_flag")
        assertTrue("the next card's flag is inside the card: $flag in $card", flag.top >= card.top && flag.bottom <= card.bottom)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun portraitPhone_flashcardFlagIsAtMost200dpTall() {
        rule.openTab("flashcards")

        val flag = rule.boundsOf("flashcard_flag")
        assertTrue(flag.height / density <= 200.5f)
        assertEquals(FlagAspectRatio, flag.width / flag.height, 0.05f)
    }

    // ---- Explore: header, plural, empty states ------------------------------------------------

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 1.5f)
    fun detailSheetWithALongOfficialName_keepsItsSpeakAndCloseButtons() {
        rule.onNodeWithTag("search_country_input").performTextInput("United K")
        rule.eventually("the UK card") { rule.onNodeWithTag("country_card_gb").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_gb").performClick()
        rule.waitForIdle()

        // "United Kingdom of Great Britain and Northern Ireland" used to squeeze these to zero width. Measure
        // the layout bounds: the 48dp touch area Material adds around an icon button survives a zero-width layout.
        val windowWidth = rule.activity.resources.displayMetrics.widthPixels
        for (target in listOf(
            rule.onNodeWithTag("speak_country_btn"),
            rule.onNodeWithContentDescription("Close")
        )) {
            val layout = target.fetchSemanticsNode().boundsInRoot
            assertTrue("button is ${layout.width / density}dp wide", layout.width / density >= 39.5f)
            assertTrue("button $layout is inside the ${windowWidth}px window", layout.right <= windowWidth)
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun detailSheet_showsRoundedFiguresLabelledAsEstimates() {
        rule.onNodeWithTag("search_country_input").performTextInput("France")
        rule.eventually("the France card") { rule.onNodeWithTag("country_card_fr").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Total Area").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Languages").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Land Area").assertCountEquals(0)
        rule.onAllNodesWithText("Official Languages").assertCountEquals(0)
        rule.onNodeWithText("est.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("km²", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun detailSheet_ofLeftHandTrafficCountrySaysLeft() {
        rule.onNodeWithTag("search_country_input").performTextInput("Thailand")
        rule.eventually("the Thailand card") { rule.onNodeWithTag("country_card_th").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_th").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Left side").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun detailSheet_ofAntarcticaDoesNotClaimADrivingSide() {
        rule.onNodeWithTag("search_country_input").performTextInput("Antarctica")
        rule.eventually("the Antarctica card") { rule.onNodeWithTag("country_card_aq").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_aq").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("No public roads").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("N/A side").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun countryCard_showsOneCapitalWhereSeveralCitiesShareTheRole() {
        rule.onNodeWithTag("search_country_input").performTextInput("South Africa")
        rule.eventually("the South Africa card") { rule.onNodeWithTag("country_card_za").assertIsDisplayed() }

        rule.onNodeWithTag("country_card_za").assertIsDisplayed()
        rule.onNodeWithText("Pretoria").assertIsDisplayed()
        rule.onAllNodesWithText("Cape Town", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun detailSheet_flagKeepsItsShape_andTheControlsSitOnIt() {
        rule.onNodeWithTag("search_country_input").performTextInput("South Africa")
        rule.eventually("the South Africa card") { rule.onNodeWithTag("country_card_za").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_za").performClick()
        rule.waitForIdle()

        val flag = rule.boundsOf("sheet_flag")
        val header = rule.boundsOf("speak_country_btn")
        val facts = rule.onNodeWithText("Country Facts").fetchSemanticsNode().boundsInRoot
        val bookmark = rule.boundsOf("sheet_favorite_btn")
        assertEquals("the flag was stretched: $flag", FlagAspectRatio, flag.width / flag.height, 0.02f)
        assertTrue("flag $flag runs into the header $header", flag.top >= header.bottom)
        assertTrue("flag $flag runs into the facts title $facts", flag.bottom <= facts.top)
        assertTrue("bookmark $bookmark is not on the flag $flag",
            bookmark.left >= flag.left && bookmark.right <= flag.right && bookmark.top >= flag.top && bookmark.bottom <= flag.bottom)
    }

    private fun assertLongestRegionPillStaysOnTheFlagAndClearOfTheBookmark() {
        val longest = allCountries.maxByOrNull { it.regionLabel.length }!!
        rule.onNodeWithTag("search_country_input").performTextInput(longest.name)
        rule.eventually("the ${longest.name} card") { rule.onNodeWithTag("country_card_${longest.code.lowercase()}").assertIsDisplayed() }
        rule.onNodeWithTag("country_card_${longest.code.lowercase()}").performClick()
        rule.waitForIdle()

        val flag = rule.boundsOf("sheet_flag")
        val pill = rule.boundsOf("sheet_region_pill")
        val bookmark = rule.boundsOf("sheet_favorite_btn")
        val label = longest.regionLabel
        assertTrue("$label: pill $pill sticks out of the flag $flag",
            pill.left >= flag.left && pill.right <= flag.right && pill.top >= flag.top && pill.bottom <= flag.bottom)
        assertTrue("$label: pill $pill runs under the bookmark $bookmark", pill.right <= bookmark.left)
        assertTrue("$label: bookmark $bookmark sticks out of the flag $flag", bookmark.right <= flag.right)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun detailSheet_longestRegionLabelStaysOnTheFlagAndClearOfTheBookmark() =
        assertLongestRegionPillStaysOnTheFlagAndClearOfTheBookmark()

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 1.5f)
    fun detailSheet_longestRegionLabelStillFitsAtLargeFontSize() =
        assertLongestRegionPillStaysOnTheFlagAndClearOfTheBookmark()

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", fontScale = 1.5f)
    fun longestFlagText_canBeReadInFullOnTheFlashcardAndInTheDetailSheet() {
        val longest = allCountries.maxByOrNull { it.flagDescription.length }!!
        val card = "country_card_${longest.code.lowercase()}"
        rule.onNodeWithTag("search_country_input").performTextInput(longest.name)
        rule.eventually("the ${longest.name} card") { rule.onNodeWithTag(card).assertIsDisplayed() }

        // The flashcard deck follows the Explore filter; the search may match several countries ("Guinea",
        // "Sudan"), so step to this one's place in the deck before flipping.
        val shown = ViewModelProvider(rule.activity)[CountryViewModel::class.java].filteredCountries.value
        rule.openTab("flashcards")
        repeat(shown.indexOfFirst { it.code == longest.code }) {
            rule.onNodeWithTag("grade_mastered_btn").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        // The flip card is clickable, so its text is merged into the card's own node, which does not scroll;
        // the unmerged node is the one inside the scrolling column.
        rule.onNodeWithText(longest.flagDescription, useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        rule.openTab("explore")
        rule.onNodeWithTag(card).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(longest.flagDescription).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun countryCard_showsTheLongestNameWithoutCuttingItOff() {
        val longest = allCountries.maxByOrNull { it.name.length }!!
        rule.onNodeWithTag("search_country_input").performTextInput(longest.name)
        rule.eventually("the ${longest.name} card") { rule.onNodeWithTag("country_card_${longest.code.lowercase()}").assertIsDisplayed() }

        // The search field also holds the typed name, so pick the node that is not editable.
        val node = rule.onNode(hasText(longest.name) and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertFalse("'${longest.name}' is cut off on its card", layouts.single().hasVisualOverflow)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun countryCount_usesTheSingularForExactlyOne() {
        rule.onNodeWithText("${allCountries.size} Countries").assertIsDisplayed()

        rule.onNodeWithTag("search_country_input").performTextInput("Japan")

        rule.eventually("one result") { rule.onNodeWithText("1 Country").assertIsDisplayed() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun noResults_explainsAndOffersToClearTheSearch() {
        rule.onNodeWithTag("search_country_input").performTextInput("zzzz-no-such-country")

        rule.eventually("the empty state") { rule.onNodeWithText("No countries found").assertIsDisplayed() }
        rule.onNodeWithTag("explore_clear_filters_btn").performClick()

        rule.eventually("the full list") { rule.onNodeWithText("${allCountries.size} Countries").assertIsDisplayed() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun savedOnlyWithNothingSaved_saysSoInsteadOfBlamingTheSearch() {
        rule.onNodeWithTag("bookmarks_filter_chip").performClick()

        rule.eventually("the saved-only empty state") { rule.onNodeWithText("No saved countries yet").assertIsDisplayed() }
        rule.onNodeWithText("Tap the bookmark on any country to save it here").assertIsDisplayed()

        rule.onNodeWithTag("explore_clear_filters_btn").performClick()
        rule.eventually("the full list") { rule.onNodeWithText("${allCountries.size} Countries").assertIsDisplayed() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun sortByContinent_isOfferedAndGroupsCountriesByContinent() {
        // Alphabetical by name puts Antarctica, Argentina, Australia first; by continent it is Africa first.
        rule.onNodeWithTag("sort_menu_btn").performClick()
        rule.onNodeWithText("Sort by Continent").performClick()
        rule.waitForIdle()

        val expected = allCountries.sortedWith(CountryRepository.byContinentThenName).take(4).map { "country_card_${it.code.lowercase()}" }
        assertTrue("test needs the first cards to differ from the by-name order", expected != cardsByName.take(4))
        val cards = expected.map { rule.boundsOf(it) }
        cards.zipWithNext().forEachIndexed { i, (a, b) ->
            val inReadingOrder = a.top < b.top - 1f || (kotlin.math.abs(a.top - b.top) <= 1f && a.left < b.left)
            assertTrue("${expected[i]} should come before ${expected[i + 1]}: $a then $b", inReadingOrder)
        }
        rule.onNodeWithTag("country_card_aq").assertDoesNotExist()
    }

    /**
     * Contrast between a node's background and the strongest solid colour found in the [from]..[to] share of
     * its width, measured on the rendered pixels so it covers whatever colours the code really chose.
     */
    private fun renderedContrast(tag: String, from: Float, to: Float): Double {
        val area = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val node = rule.drawWindow().crop(area)

        val solid = (0 until node.height).flatMap { y -> (0 until node.width).map { x -> node.getPixel(x, y) } }
            .filter { it ushr 24 == 0xFF }
        val background = Color(solid.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key)
        // Only the middle band of rows: a pill-shaped chip's corners show the page behind it, which would
        // count as "contrast" even when the label itself is unreadable.
        val rows = (node.height / 4) until (node.height * 3 / 4)
        val part = rows.flatMap { y ->
            ((node.width * from).toInt() until (node.width * to).toInt()).map { x -> node.getPixel(x, y) }
        }.filter { it ushr 24 == 0xFF }
        return part.maxOf { contrastRatio(background, Color(it)) }
    }

    private fun assertSelectedSavedChipIsReadable() {
        rule.onNodeWithTag("bookmarks_filter_chip").performClick()
        rule.waitForIdle()
        // Left third: the bookmark icon. Right half: the "Saved" label.
        val icon = renderedContrast("bookmarks_filter_chip", 0.08f, 0.33f)
        val label = renderedContrast("bookmarks_filter_chip", 0.5f, 0.9f)
        assertTrue("icon contrast is %.2f:1".format(icon), icon >= 4.5)
        assertTrue("label contrast is %.2f:1".format(label), label >= 4.5)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun selectedSavedChip_isReadableAsRenderedInTheLightTheme() = assertSelectedSavedChipIsReadable()

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun selectedSavedChip_isReadableAsRenderedInTheDarkTheme() = assertSelectedSavedChipIsReadable()

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun smallText_isNotStretchedToTheBodyLineHeight() {
        // The 10sp country-code chip used to inherit a 24sp line height and came out about 24dp tall.
        val code = rule.onNodeWithText(byName.first().code, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("code chip text is ${code.height / density}dp tall", code.height / density <= 16.5f)
    }

    // ---- Decorative content stays quiet for TalkBack ---------------------------------------------

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun decorativeImagesAndNavIcons_haveNoDescription() {
        // Each bottom-nav tab is announced once by its label, not again by its icon.
        // Unmerged tree: the merged nav item would hide an icon's own description.
        for (description in listOf("Explore", "Flashcards", "Quiz", "Progress", "World Map Banner", "Search icon")) {
            rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // ---- Accessibility semantics ----------------------------------------------------------------

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun bookmarkButton_isAToggleThatReflectsItsState() {
        val button = rule.onNodeWithTag("favorite_btn_${byName.first().code.lowercase()}")
        button.assertIsToggleable().assertIsOff()

        button.performClick()

        rule.eventually("the bookmark to be saved") { rule.onNodeWithTag("favorite_btn_${byName.first().code.lowercase()}").assertIsOn() }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun continentChips_exposeWhichOneIsSelected() {
        rule.onNodeWithTag("continent_chip_all").assertIsSelected()
        // With real font widths the chip row is wider than the screen, so scroll to the next chip.
        rule.onNodeWithTag("continent_chips").performScrollToNode(hasTestTag("continent_chip_europe"))
        rule.onNodeWithTag("continent_chip_europe").assertIsNotSelected()

        rule.onNodeWithTag("continent_chip_europe").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("continent_chip_europe").assertIsSelected()
        rule.onNodeWithTag("continent_chips").performScrollToNode(hasTestTag("continent_chip_all"))
        rule.onNodeWithTag("continent_chip_all").assertIsNotSelected()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
    fun flipCard_announcesWhichSideIsShowing() {
        rule.openTab("flashcards")
        val card: SemanticsNodeInteraction = rule.onNodeWithTag("flashcard_flip_card")
        card.assert(stateDescriptionIs("Showing the flag"))

        card.performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_flip_card").assert(stateDescriptionIs("Showing country details"))
    }
}
