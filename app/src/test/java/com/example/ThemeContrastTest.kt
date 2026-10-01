package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pairs the UI relies on for text on coloured surfaces must meet WCAG AA (4.5:1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeContrastTest {

  @get:Rule val rule = createComposeRule()

  private fun contrast(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
  }

  private fun schemeFor(dark: Boolean): ColorScheme {
    lateinit var scheme: ColorScheme
    rule.setContent { WorldFlagsTheme(darkTheme = dark) { scheme = MaterialTheme.colorScheme } }
    rule.waitForIdle()
    return scheme
  }

  @Test
  fun selectedSavedChip_labelIsReadableInTheLightTheme() {
    val s = schemeFor(dark = false)
    assertTrue("onSecondary on secondary = ${contrast(s.onSecondary, s.secondary)}", contrast(s.onSecondary, s.secondary) >= 4.5)
  }

  @Test
  fun selectedSavedChip_labelIsReadableInTheDarkTheme() {
    val s = schemeFor(dark = true)
    assertTrue("onSecondary on secondary = ${contrast(s.onSecondary, s.secondary)}", contrast(s.onSecondary, s.secondary) >= 4.5)
  }

  @Test
  fun theOldChipColours_wouldHaveFailedInTheLightTheme() {
    // Documents the bug: black label on the light theme's navy secondary colour.
    val s = schemeFor(dark = false)
    assertTrue(contrast(Color.Black, s.secondary) < 3.0)
  }
}
