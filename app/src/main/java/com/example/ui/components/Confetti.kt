package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.progress.Rewards
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_MILLIS = 2800
private val CONFETTI_COLOURS = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA)
)

/** One falling piece: where it starts across the width, when it sets off, how fast it falls, and how it sways and spins. */
private class Piece(random: Random) {
    val x = random.nextFloat()
    val delay = random.nextFloat() * 0.35f
    val speed = 0.65f + random.nextFloat() * 0.5f
    val sway = 0.01f + random.nextFloat() * 0.04f
    val phase = random.nextFloat() * (2 * PI).toFloat()
    val size = 8f + random.nextFloat() * 8f
    val spin = 180f + random.nextFloat() * 540f
    val colour = CONFETTI_COLOURS[random.nextInt(CONFETTI_COLOURS.size)]
}

/**
 * A shower of confetti over the whole window for a good or perfect result, once per [key] (a rotation does not start it
 * again). It draws nothing for [Rewards.Celebration.NONE], when the system has animations switched off, and once it has
 * fallen; it takes no touches. Decoration only: it has no description.
 */
@Composable
fun Confetti(celebration: Rewards.Celebration, key: Any, modifier: Modifier = Modifier) {
    if (celebration == Rewards.Celebration.NONE) return
    val context = LocalContext.current
    val animated = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }
    var done by rememberSaveable(key) { mutableStateOf(!animated) }
    if (done) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearEasing))
        done = true
    }
    val pieces = remember(celebration) {
        val random = Random(celebration.ordinal)
        List(if (celebration == Rewards.Celebration.PERFECT) 140 else 70) { Piece(random) }
    }
    val t = progress.value
    Canvas(modifier = modifier.fillMaxSize().testTag("quiz_confetti")) {
        pieces.forEach { piece ->
            val local = ((t - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f) * piece.speed
            if (local <= 0f) return@forEach
            val x = (piece.x + sin(local * 6f + piece.phase) * piece.sway) * size.width
            val y = (local * 1.25f - 0.1f) * size.height
            val fade = if (t > 0.8f) (1f - t) / 0.2f else 1f
            rotate(degrees = piece.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.colour.copy(alpha = fade.coerceIn(0f, 1f)),
                    topLeft = Offset(x - piece.size / 2, y - piece.size / 4),
                    size = Size(piece.size, piece.size / 2)
                )
            }
        }
    }
}
