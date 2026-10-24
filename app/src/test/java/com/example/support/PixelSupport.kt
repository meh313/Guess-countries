package com.example.support

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.io.File

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

/**
 * The newest dialog drawn into a plain bitmap. A dialog is a window of its own, so [drawWindow] leaves it out; the
 * dialog's content is sized as it would be on screen (the dialog window is narrower than the activity's).
 */
fun drawLatestDialog(): Bitmap {
    shadowOf(Looper.getMainLooper()).idle()
    val view = ShadowDialog.getLatestDialog().window!!.decorView
    val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    return bitmap
}

/**
 * The newest dialog drawn over the activity's window, as the screen shows it. On its own ([drawLatestDialog]) a
 * sheet's scrim and the corners beside its rounded top lie over transparent pixels; here they lie over the screen
 * beneath. A dialog smaller than the window is drawn at its centre, where the system puts it.
 */
fun AndroidComposeTestRule<*, *>.drawLatestDialogOverWindow(): Bitmap {
    val bitmap = drawWindow()
    val dialog = drawLatestDialog()
    Canvas(bitmap).drawBitmap(dialog, (bitmap.width - dialog.width) / 2f, (bitmap.height - dialog.height) / 2f, null)
    return bitmap
}

/**
 * Writes the bitmap to [file] as a PNG. With [alpha] false the file has no alpha channel (a 24-bit PNG, what Google
 * Play takes for screenshots and the feature graphic). That needs every pixel opaque already, which is checked:
 * dropping the channel would otherwise turn a translucent pixel into a dark one.
 */
fun Bitmap.writePng(file: File, alpha: Boolean = true) {
    if (!alpha) {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        val translucent = pixels.count { Color.alpha(it) != 255 }
        check(translucent == 0) { "${file.name}: $translucent pixels are not opaque" }
        setHasAlpha(false)
    }
    file.parentFile?.mkdirs()
    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
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

/**
 * A store picture: [caption] in white on the brand navy, the screen beneath it in a rounded frame that runs off the
 * bottom edge, as a phone held in the hand would. The result is as big as the screen and fully opaque.
 */
fun Bitmap.captioned(caption: String, navy: Int = Color.rgb(1, 26, 80)): Bitmap {
    val unit = width / 1080f
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(navy)
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 78 * unit
    }
    val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, text, (width - 120 * unit).toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build()
    canvas.save()
    canvas.translate(60 * unit, 70 * unit)
    layout.draw(canvas)
    canvas.restore()
    val shownWidth = width * 0.84f
    val shownHeight = shownWidth * height / width
    val left = (width - shownWidth) / 2
    val top = 330 * unit
    val frame = RectF(left, top, left + shownWidth, top + shownHeight)
    val radius = 56 * unit
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) })
    canvas.drawBitmap(this, null, frame, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6 * unit
        color = Color.rgb(246, 201, 14)
    }
    canvas.drawRoundRect(frame, radius, radius, edge)
    return out
}
