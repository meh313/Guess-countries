package com.example.support

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.robolectric.Shadows.shadowOf

/**
 * The window drawn into a plain bitmap. This works under native graphics. Compose's captureToImage does
 * not: it waits for a frame that Robolectric only runs when the test thread yields.
 */
fun AndroidComposeTestRule<*, *>.drawWindow(): Bitmap {
    shadowOf(Looper.getMainLooper()).idle()
    val window = activity.window.decorView
    val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
    window.draw(Canvas(bitmap))
    return bitmap
}

/** The part of the bitmap inside [area], which is in pixels, like a node's bounds. */
fun Bitmap.crop(area: Rect): Bitmap =
    Bitmap.createBitmap(this, area.left.toInt(), area.top.toInt(), area.width.toInt(), area.height.toInt())

/** A coarse name for a pixel's colour, good enough to tell flag colours apart. */
fun colourName(argb: Int): String {
    val hsv = FloatArray(3)
    Color.colorToHSV(argb, hsv)
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    return when {
        v < 0.22f -> "black"
        s < 0.15f && v > 0.8f -> "white"
        s < 0.15f -> "gray"
        h < 15f || h >= 335f -> "red"
        h < 45f -> "orange"
        h < 70f -> "yellow"
        h < 170f -> "green"
        h < 265f -> "blue"
        else -> "purple"
    }
}

/**
 * The colour most pixels have inside the box, whose edges are fractions of the bitmap's width and height,
 * and the share of the box's pixels that colour covers.
 */
fun Bitmap.dominantColour(left: Float, top: Float, right: Float, bottom: Float): Pair<String, Float> {
    val counts = HashMap<String, Int>()
    var total = 0
    for (y in (top * height).toInt() until (bottom * height).toInt()) {
        for (x in (left * width).toInt() until (right * width).toInt()) {
            val name = colourName(getPixel(x, y))
            counts[name] = (counts[name] ?: 0) + 1
            total++
        }
    }
    val best = counts.maxByOrNull { it.value }!!
    return best.key to best.value.toFloat() / total
}
