package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Country

@Composable
fun FlagView(
    country: Country,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.5f, // Standard flag ratio 3:2
    showEmojiOverlay: Boolean = false
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (country.code) {
                "FR" -> { // Vertical Blue, White, Red
                    drawRect(Color(0xFF002395), size = Size(w / 3f, h))
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                    drawRect(Color(0xFFED2939), topLeft = Offset(2 * w / 3f, 0f), size = Size(w / 3f, h))
                }
                "DE" -> { // Horizontal Black, Red, Gold
                    drawRect(Color(0xFF000000), size = Size(w, h / 3f))
                    drawRect(Color(0xFFFF0000), topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                    drawRect(Color(0xFFFFCC00), topLeft = Offset(0f, 2 * h / 3f), size = Size(w, h / 3f))
                }
                "IT" -> { // Vertical Green, White, Red
                    drawRect(Color(0xFF009246), size = Size(w / 3f, h))
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                    drawRect(Color(0xFFCE2B37), topLeft = Offset(2 * w / 3f, 0f), size = Size(w / 3f, h))
                }
                "NG" -> { // Vertical Green, White, Green
                    drawRect(Color(0xFF008751), size = Size(w / 3f, h))
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                    drawRect(Color(0xFF008751), topLeft = Offset(2 * w / 3f, 0f), size = Size(w / 3f, h))
                }
                "JP" -> { // White with Red circle
                    drawRect(Color.White, size = size)
                    drawCircle(Color(0xFFBC002D), radius = h * 0.3f, center = center)
                }
                "UA" -> { // Blue & Yellow horizontal
                    drawRect(Color(0xFF0057B7), size = Size(w, h / 2f))
                    drawRect(Color(0xFFFFD700), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
                }
                "SE" -> { // Nordic Cross Blue & Yellow
                    drawRect(Color(0xFF006AA7), size = size)
                    // Cross horizontal
                    drawRect(Color(0xFFFECC00), topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.2f))
                    // Cross vertical
                    drawRect(Color(0xFFFECC00), topLeft = Offset(w * 0.3f, 0f), size = Size(w * 0.15f, h))
                }
                "NO" -> { // Red with white & blue Nordic cross
                    drawRect(Color(0xFFBA0C2F), size = size)
                    // White outer cross
                    drawRect(Color.White, topLeft = Offset(0f, h * 0.35f), size = Size(w, h * 0.3f))
                    drawRect(Color.White, topLeft = Offset(w * 0.28f, 0f), size = Size(w * 0.22f, h))
                    // Inner blue cross
                    drawRect(Color(0xFF00205B), topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.2f))
                    drawRect(Color(0xFF00205B), topLeft = Offset(w * 0.32f, 0f), size = Size(w * 0.14f, h))
                }
                "ES" -> { // Red, double Yellow, Red
                    drawRect(Color(0xFFAA1523), size = Size(w, h * 0.25f))
                    drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.5f))
                    drawRect(Color(0xFFAA1523), topLeft = Offset(0f, h * 0.75f), size = Size(w, h * 0.25f))
                    // Emblem circle
                    drawCircle(Color(0xFF8B1200), radius = h * 0.15f, center = Offset(w * 0.3f, h * 0.5f))
                }
                "CA" -> { // Red, White, Red with maple leaf emblem
                    drawRect(Color(0xFFFF0000), size = Size(w * 0.25f, h))
                    drawRect(Color.White, topLeft = Offset(w * 0.25f, 0f), size = Size(w * 0.5f, h))
                    drawRect(Color(0xFFFF0000), topLeft = Offset(w * 0.75f, 0f), size = Size(w * 0.25f, h))
                    // Diamond Maple representation
                    drawCircle(Color(0xFFFF0000), radius = h * 0.22f, center = center)
                    drawCircle(Color.White, radius = h * 0.10f, center = Offset(w * 0.5f, h * 0.42f))
                }
                "BR" -> { // Green field, Yellow rhombus, Blue circle
                    drawRect(Color(0xFF009739), size = size)
                    val rhombus = Path().apply {
                        moveTo(w * 0.5f, h * 0.12f)
                        lineTo(w * 0.88f, h * 0.5f)
                        lineTo(w * 0.5f, h * 0.88f)
                        lineTo(w * 0.12f, h * 0.5f)
                        close()
                    }
                    drawPath(rhombus, Color(0xFFFEDD00))
                    drawCircle(Color(0xFF012169), radius = h * 0.25f, center = center)
                }
                "US" -> { // USA Canton & Stripes
                    val stripeH = h / 7f
                    for (i in 0..6) {
                        val color = if (i % 2 == 0) Color(0xFFB22234) else Color.White
                        drawRect(color, topLeft = Offset(0f, i * stripeH), size = Size(w, stripeH))
                    }
                    // Blue Canton
                    drawRect(Color(0xFF3C3B6E), size = Size(w * 0.45f, stripeH * 4f))
                }
                "GB" -> { // Union Jack
                    drawRect(Color(0xFF00247D), size = size)
                    // White diagonals
                    drawLine(Color.White, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = h * 0.2f)
                    drawLine(Color.White, start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = h * 0.2f)
                    // Red diagonals
                    drawLine(Color(0xFFCF142B), start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = h * 0.08f)
                    drawLine(Color(0xFFCF142B), start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = h * 0.08f)
                    // White cross
                    drawRect(Color.White, topLeft = Offset(0f, h * 0.35f), size = Size(w, h * 0.3f))
                    drawRect(Color.White, topLeft = Offset(w * 0.4f, 0f), size = Size(w * 0.2f, h))
                    // Red cross
                    drawRect(Color(0xFFCF142B), topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.2f))
                    drawRect(Color(0xFFCF142B), topLeft = Offset(w * 0.43f, 0f), size = Size(w * 0.14f, h))
                }
                "EG" -> { // Red, White, Black + Gold Eagle
                    drawRect(Color(0xFFC8102E), size = Size(w, h / 3f))
                    drawRect(Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                    drawRect(Color.Black, topLeft = Offset(0f, 2 * h / 3f), size = Size(w, h / 3f))
                    drawCircle(Color(0xFFC09300), radius = h * 0.12f, center = center)
                }
                "AR" -> { // Light Blue, White, Light Blue
                    drawRect(Color(0xFF75AADB), size = Size(w, h / 3f))
                    drawRect(Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                    drawRect(Color(0xFF75AADB), topLeft = Offset(0f, 2 * h / 3f), size = Size(w, h / 3f))
                    drawCircle(Color(0xFFF6B40E), radius = h * 0.12f, center = center)
                }
                "IN" -> { // Saffron, White, Green
                    drawRect(Color(0xFFFF9933), size = Size(w, h / 3f))
                    drawRect(Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                    drawRect(Color(0xFF138808), topLeft = Offset(0f, 2 * h / 3f), size = Size(w, h / 3f))
                    drawCircle(Color(0xFF000080), radius = h * 0.12f, center = center)
                }
                "CN" -> { // Red field + Yellow stars
                    drawRect(Color(0xFFDE2910), size = size)
                    drawCircle(Color(0xFFFFDE00), radius = h * 0.2f, center = Offset(w * 0.2f, h * 0.3f))
                }
                "AU", "NZ" -> { // Blue Canton Union Jack style
                    drawRect(Color(0xFF000085), size = size)
                    drawRect(Color(0xFF00247D), size = Size(w * 0.45f, h * 0.5f))
                    // Stars representation
                    drawCircle(Color.White, radius = h * 0.08f, center = Offset(w * 0.75f, h * 0.3f))
                    drawCircle(Color.White, radius = h * 0.08f, center = Offset(w * 0.82f, h * 0.6f))
                    drawCircle(Color.White, radius = h * 0.08f, center = Offset(w * 0.65f, h * 0.7f))
                }
                "MA" -> { // Red with green star
                    drawRect(Color(0xFFC1272D), size = size)
                    drawCircle(Color(0xFF006233), radius = h * 0.22f, center = center)
                }
                "AQ" -> { // Antarctic Sky blue with white continent silhouette
                    drawRect(Color(0xFF6B9AC4), size = size)
                    drawCircle(Color.White, radius = h * 0.32f, center = center)
                }
                else -> { // Generic stripe / emoji flag fallback
                    val colors = country.flagColors
                    val c1 = parseColor(colors.getOrNull(0) ?: "Blue")
                    val c2 = parseColor(colors.getOrNull(1) ?: "White")
                    val c3 = parseColor(colors.getOrNull(2) ?: "Red")

                    if (country.flagType == com.example.data.model.FlagStyle.VERTICAL_STRIPES_3) {
                        drawRect(c1, size = Size(w / 3f, h))
                        drawRect(c2, topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                        drawRect(c3, topLeft = Offset(2 * w / 3f, 0f), size = Size(w / 3f, h))
                    } else if (country.flagType == com.example.data.model.FlagStyle.HORIZONTAL_STRIPES_2) {
                        drawRect(c1, size = Size(w, h / 2f))
                        drawRect(c2, topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
                    } else {
                        drawRect(c1, size = Size(w, h / 3f))
                        drawRect(c2, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                        drawRect(c3, topLeft = Offset(0f, 2 * h / 3f), size = Size(w, h / 3f))
                    }
                }
            }
        }

        // Crisp Emoji Overlay & Badge
        Text(
            text = country.flagEmoji,
            fontSize = 32.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private fun parseColor(name: String): Color {
    return when (name.lowercase()) {
        "blue", "light blue" -> Color(0xFF1E56A0)
        "red", "saffron" -> Color(0xFFD32F2F)
        "green" -> Color(0xFF2E7D32)
        "yellow", "gold" -> Color(0xFFFBC02D)
        "black" -> Color(0xFF212121)
        "white" -> Color(0xFFFFFFFF)
        else -> Color(0xFF1976D2)
    }
}
