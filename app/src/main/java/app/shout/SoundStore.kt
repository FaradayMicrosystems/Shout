package app.shout

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Sound(val id: String, val name: String, val uri: String)

/** Saves the soundboard list (name + file location) in app preferences. */
class SoundStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("sounds", Context.MODE_PRIVATE)
    val sounds = mutableStateListOf<Sound>().apply { addAll(load()) }

    private fun load(): List<Sound> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Sound(o.getString("id"), o.getString("name"), o.getString("uri"))
        }
    }

    private fun save() {
        val arr = JSONArray()
        sounds.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun add(name: String, uri: Uri) {
        sounds.add(Sound(UUID.randomUUID().toString(), name, uri.toString()))
        save()
    }

    /**
     * Copies a picked file into the app and adds it. Copying (instead of pointing at the original)
     * works with every file source (Downloads, Drive, Files apps) and keeps working if the original
     * is moved or deleted. Returns false if Android can't play the file.
     */
    fun importSound(name: String, uri: Uri): Boolean {
        if (!isPlayableAudio(uri)) return false
        val ext = extension(uri)
        val dir = File(context.filesDir, "sounds").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        return try {
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            add(name, Uri.fromFile(out))
            true
        } catch (e: Exception) { out.delete(); false }
    }

    private fun extension(uri: Uri): String {
        var ext = ""
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) ext = it.getString(0)?.substringAfterLast('.', "") ?: ""
            }
        }
        if (ext.isBlank()) ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "audio"
        return ext.lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "audio" }
    }

    /** True if Android's media player can open it (MP3, WAV, M4A/AAC, OGG, FLAC, OPUS, AMR, MIDI...). */
    fun isPlayableAudio(uri: Uri): Boolean {
        runCatching {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                if (r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") return true
            } finally { r.release() }
        }
        // Some files (raw WAV, odd MP3 headers) confuse the metadata reader; try the real player.
        return runCatching {
            val mp = android.media.MediaPlayer()
            try { mp.setDataSource(context, uri); mp.prepare(); true } finally { mp.release() }
        }.getOrDefault(false)
    }

    fun rename(sound: Sound, name: String) {
        val i = sounds.indexOfFirst { it.id == sound.id }
        if (i >= 0) { sounds[i] = sound.copy(name = name); save() }
    }

    fun delete(sound: Sound) {
        sounds.removeAll { it.id == sound.id }
        save()
        val uri = Uri.parse(sound.uri)
        if (uri.scheme == "file") uri.path?.let { File(it).delete() } // our own copies and recordings
        else runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun newRecordingFile(): File =
        File(context.filesDir, "recordings").apply { mkdirs() }
            .let { File(it, "rec_${System.currentTimeMillis()}.m4a") }
}
