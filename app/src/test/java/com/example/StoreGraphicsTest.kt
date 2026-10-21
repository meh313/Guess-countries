package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.support.drawWindow
import com.example.support.writePng
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.OceanBlueSecondary
import com.example.ui.theme.WorldFlagsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Play Store listing's two graphics besides the screenshots, drawn from the launcher icon's own artwork and
 * colour at one pixel per dp: the 1024x500 feature graphic, a 24-bit PNG with no alpha channel as Play wants it, and
 * the 512x512 icon, a 32-bit PNG. Both are written to `build/screenshots/store-graphics/`; after a run, its two files
 * are copied into `store/` in the repository, where `tools/store-check.py` checks them against Play's rules.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StoreGraphicsTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val folder = File("build/screenshots/store-graphics")

    /** The launcher icon's background (ic_launcher_background.xml), the navy the flag ring is drawn to sit on. */
    private val iconNavy = Color(0xFF001B5F)

    /**
     * The flag ring on the start side and the name with the listing's pitch beside it, on the icon's navy shading
     * into the app's deep blue. The words are the listing's (store/listing.md), in English like its default language.
     */
    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun storeFeatureGraphic() {
        rule.setContent {
            WorldFlagsTheme(darkTheme = false) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(0.45f to iconNavy, 1f to OceanBlueSecondary))
                        .padding(horizontal = 58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(44.dp)
                ) {
                    FlagRing(Modifier.size(384.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Learn every flag,\ncapital and location",
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 38.sp
                        )
                        Text("196 countries · offline · no ads", color = DarkPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        save("feature-graphic", 1024, 500, alpha = false)
    }

    /**
     * The flag ring on the icon's navy, the whole square filled: Play rounds the corners itself and asks for an icon
     * without them. The artwork is drawn at its own 384 pixels, so nothing is scaled up, with a margin round it like
     * the one in assets/app-icon-master.webp.
     */
    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun storeIcon() {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(iconNavy), contentAlignment = Alignment.Center) {
                FlagRing(Modifier.size(384.dp))
            }
        }
        save("icon-512", 512, 512, alpha = true)
    }

    @Composable
    private fun FlagRing(modifier: Modifier) =
        Image(painterResource(R.drawable.ic_launcher_art), contentDescription = null, modifier = modifier)

    /** Writes the window to `build/screenshots/store-graphics/<name>.png`, checking it is the size Play takes. */
    private fun save(name: String, width: Int, height: Int, alpha: Boolean) {
        rule.waitForIdle()
        val bitmap = rule.drawWindow()
        assertEquals(width to height, bitmap.width to bitmap.height)
        bitmap.writePng(File(folder, "$name.png"), alpha)
    }
}
