package com.example.ui.components

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small, cached copies of the flag artwork for grid cells and thumbnails.
 *
 * Every flag ships as a 600×450 lossless WebP. Drawing one through `painterResource` decodes the whole file on
 * the main thread during composition, which a scrolling grid does for every cell that comes into view. Here a
 * flag is decoded once per width bucket, with [BitmapFactory.Options.inSampleSize] chosen so the decoded bitmap is
 * never narrower than the bucket (so it is never scaled up), and kept in a memory cache of about an eighth of
 * the heap, so coming back to a flag costs nothing.
 */
object FlagBitmaps {

    /** What a cached bitmap is filed under: the drawable and the width bucket it was decoded for. */
    data class Key(@DrawableRes val resId: Int, val bucketPx: Int)

    /** The bucket for a box too wide for the others, or of unknown width: the artwork at its full size. */
    const val FULL_WIDTH: Int = Int.MAX_VALUE

    /** Widths, in pixels, a thumbnail is decoded for; a request gets the smallest bucket at least as wide as it. */
    private val buckets = intArrayOf(128, 256, 512)

    private val cache = object : LruCache<Key, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: Key, value: ImageBitmap): Int = (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
    }

    private fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(1024)

    /** The width bucket a box [targetWidthPx] pixels wide is served from. */
    fun bucketFor(targetWidthPx: Int): Int = buckets.firstOrNull { it >= targetWidthPx } ?: FULL_WIDTH

    /**
     * The largest power-of-two [BitmapFactory.Options.inSampleSize] that still decodes a [sourceWidth]-pixel image to
     * at least [targetWidth] pixels wide: 600 → 1 for a 600 target, 2 for 300, 4 for 150, 8 for 75.
     */
    fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
        if (sourceWidth <= 0 || targetWidth <= 0) return 1
        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= targetWidth) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** The cached bitmap for a flag at this width, or null when it has not been decoded yet. */
    fun peek(@DrawableRes resId: Int, targetWidthPx: Int): ImageBitmap? = peek(Key(resId, bucketFor(targetWidthPx)))

    internal fun peek(key: Key): ImageBitmap? = cache.get(key)

    /**
     * The bitmap for a flag at this width, decoded now (on the calling thread) unless it is already cached. Two calls
     * for the same flag and width return the same instance. Null only if the drawable cannot be decoded.
     */
    fun load(resources: Resources, @DrawableRes resId: Int, targetWidthPx: Int): ImageBitmap? =
        load(resources, Key(resId, bucketFor(targetWidthPx)))

    internal fun load(resources: Resources, key: Key): ImageBitmap? {
        cache.get(key)?.let { return it }
        val decoded = decode(resources, key) ?: return null
        // Another thread may have finished the same decode first; keep whichever is in the cache so callers share one instance.
        synchronized(cache) {
            cache.get(key)?.let { return it }
            cache.put(key, decoded)
            return decoded
        }
    }

    private fun decode(resources: Resources, key: Key): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, key.resId, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, key.bucketPx) }
        return BitmapFactory.decodeResource(resources, key.resId, options)?.asImageBitmap()
    }
}

/**
 * The flag artwork [resId] decoded for a box [targetWidthPx] pixels wide, from [FlagBitmaps].
 *
 * A flag already in the cache is returned at once, so a recomposition never flickers through a placeholder. One
 * that is not is decoded on [Dispatchers.IO] and arrives on a later frame; until then this is null.
 */
@Composable
fun rememberFlagBitmap(@DrawableRes resId: Int, targetWidthPx: Int): ImageBitmap? {
    val resources = LocalResources.current
    val cacheKey = FlagBitmaps.Key(resId, FlagBitmaps.bucketFor(targetWidthPx))
    // A new flag or bucket starts from its own cached value instead of showing the previous flag for a frame.
    return key(cacheKey) {
        produceState<ImageBitmap?>(initialValue = FlagBitmaps.peek(cacheKey), resources) {
            if (value == null) {
                value = FlagBitmaps.peek(cacheKey) ?: withContext(Dispatchers.IO) { FlagBitmaps.load(resources, cacheKey) }
            }
        }.value
    }
}
