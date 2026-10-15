package com.example.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.R

/** The quiz's three feedback sounds (short chimes written by `tools/gen-sounds.py`). */
enum class Sound(@param:RawRes val res: Int) {
    CORRECT(R.raw.answer_correct),
    WRONG(R.raw.answer_wrong),
    FINISHED(R.raw.quiz_finished)
}

/**
 * Plays the [Sound]s through a [SoundPool] on the game audio stream, so they follow the media volume and never
 * interrupt music. The pool loads the three files once; a play before a file is loaded is dropped, not queued.
 */
class SoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loaded = HashSet<Int>()
    private val ids: Map<Sound, Int>

    /** What was asked to play, in order, for the tests (Robolectric's pool makes no sound). */
    @VisibleForTesting internal val played = ArrayList<Sound>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        ids = Sound.entries.associateWith { pool.load(context, it.res, 1) }
    }

    fun play(sound: Sound) {
        played += sound
        val id = ids[sound] ?: return
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()

    companion object {
        /** The player most recently made by [rememberSoundPlayer], for the tests; null once released. */
        @VisibleForTesting internal var latest: SoundPlayer? = null
    }
}

/** A [SoundPlayer] for the composition while [enabled], released when it leaves or the setting goes off; null when off. */
@Composable
fun rememberSoundPlayer(enabled: Boolean): SoundPlayer? {
    val context = LocalContext.current.applicationContext
    val player = remember(enabled) { if (enabled) SoundPlayer(context) else null }
    DisposableEffect(player) {
        SoundPlayer.latest = player
        onDispose {
            if (SoundPlayer.latest === player) SoundPlayer.latest = null
            player?.release()
        }
    }
    return player
}
