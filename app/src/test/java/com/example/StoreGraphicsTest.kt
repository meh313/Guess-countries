package com.example

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CountryCatalog
import com.example.support.drawWindow
import com.example.support.writePng
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.OceanBlueSecondary
import com.example.ui.theme.WorldFlagsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Play Store listing's two graphics besides the screenshots, drawn from the icon's master art at one pixel per
 * dp: the 1024x500 feature graphic, a 24-bit PNG with no alpha channel as Play wants it, and the 512x512 icon, a
 * 32-bit PNG. Both are written to `build/screenshots/store-graphics/`; after a run, its two files are copied into
 * `store/` in the repository, where `tools/store-check.py` checks them against Play's rules. The art is read from the
 * repository's `assets/` (the master, and the pin tools/gen-app-icon.py cuts from it), as the app only carries the
 * launcher's own sizes of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StoreGraphicsTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val folder = File("build/screenshots/store-graphics")

    /** The navy at the edges of the icon's master, the colour the pin sits on. */
    private val iconNavy = Color(0xFF011A50)

    /**
     * The pin on the start side and the name with the listing's pitch beside it, on the icon's navy shading into the
     * app's deep blue. The words are the listing's (store/listing.md), in English like its default language.
     */
    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun storeFeatureGraphic() {
        val pin = art("app-icon-pin.webp")
        rule.setContent {
            WorldFlagsTheme(darkTheme = false) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(0.45f to iconNavy, 1f to OceanBlueSecondary))
                        .padding(horizontal = 58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally)
                ) {
                    // The pin stands in the middle of a square: the square at the graphic's height, its empty sides cut.
                    Image(pin, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(300.dp, 500.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Learn every flag,\ncapital and location",
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 38.sp
                        )
                        Text("${countries()} countries · works offline", color = DarkPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        save("feature-graphic", 1024, 500, alpha = false)
    }

    /** The master itself, the whole square filled: Play rounds the corners itself and asks for an icon without them. */
    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun storeIcon() {
        val master = art("app-icon-master.webp")
        rule.setContent {
            Image(master, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        save("icon-512", 512, 512, alpha = true)
    }

    /** The countries the app teaches, counted the way Progress counts them: Antarctica is in the catalog but not one. */
    private fun countries() = CountryCatalog.all.count { it.isSovereign }

    /** One of the icon's images in the repository's `assets/`; the unit tests run in `app/`. */
    private fun art(name: String): ImageBitmap {
        val bitmap = BitmapFactory.decodeFile(File("../assets", name).path)
        assertNotNull("../assets/$name", bitmap)
        return bitmap.asImageBitmap()
    }

    /** Writes the window to `build/screenshots/store-graphics/<name>.png`, checking it is the size Play takes. */
    private fun save(name: String, width: Int, height: Int, alpha: Boolean) {
        rule.waitForIdle()
        val bitmap = rule.drawWindow()
        assertEquals(width to height, bitmap.width to bitmap.height)
        bitmap.writePng(File(folder, "$name.png"), alpha)
    }
}
