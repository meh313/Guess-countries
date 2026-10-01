package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Country

/**
 * The shape of every flag box: 4:3, the shape the bundled artwork is drawn in, so circles and stars stay
 * round. (Stretching it to 3:2 turned Japan's disc into an ellipse.)
 */
const val FlagAspectRatio = 4f / 3f

/**
 * A country's flag, drawn from the bundled artwork ([flagArtFor]) in a [FlagAspectRatio] box.
 *
 * A country without artwork gets a plain placeholder carrying the device's own flag emoji, so adding a
 * country to the catalog never crashes or shows a wrong flag.
 */
@Composable
fun FlagView(
    country: Country,
    modifier: Modifier = Modifier,
    aspectRatio: Float = FlagAspectRatio,
    /** Draws the flag emoji over the artwork. Off by default: the artwork is the flag. */
    showEmojiOverlay: Boolean = false,
    /**
     * What a screen reader says for this flag. Null leaves it silent (the country name is next to it).
     * Quiz and flashcard fronts pass a neutral text because the name is the answer. The emoji is never
     * read out.
     */
    contentDescription: String? = null
) {
    val art = flagArtFor(country.code)
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        if (art == null || showEmojiOverlay) {
            Text(text = country.flagEmoji, fontSize = if (art == null) 48.sp else 32.sp)
        }
    }
}
