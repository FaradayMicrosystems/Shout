package app.shout

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf

/**
 * Plays soundboard clips. Uses the MEDIA audio stream, so Android routes it to the
 * connected Bluetooth speaker automatically. Several clips can overlap.
 */
class Player(private val context: Context) {
    private val players = mutableMapOf<String, MediaPlayer>()
    val playing = mutableStateMapOf<String, Boolean>()

    /** Tap = play from the start. Tap again while playing = stop. */
    fun toggle(sound: Sound, onError: (String) -> Unit) {
        if (players.containsKey(sound.id)) { stop(sound.id); return }
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context, Uri.parse(sound.uri))
            mp.setOnCompletionListener { stop(sound.id) }
            mp.prepare()
            mp.start()
            players[sound.id] = mp
            playing[sound.id] = true
        } catch (e: Exception) {
            onError("Can't play \"${sound.name}\". The file may have been moved or deleted.")
        }
    }

    fun stop(id: String) {
        players.remove(id)?.let { runCatching { it.stop() }; it.release() }
        playing.remove(id)
    }

    fun stopAll() = players.keys.toList().forEach { stop(it) }
}
