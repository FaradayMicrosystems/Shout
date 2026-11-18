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

    fun rename(sound: Sound, name: String) {
        val i = sounds.indexOfFirst { it.id == sound.id }
        if (i >= 0) { sounds[i] = sound.copy(name = name); save() }
    }

    fun delete(sound: Sound) {
        sounds.removeAll { it.id == sound.id }
        save()
        val uri = Uri.parse(sound.uri)
        if (uri.scheme == "file") uri.path?.let { File(it).delete() } // our own recordings
        else runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun newRecordingFile(): File =
        File(context.filesDir, "recordings").apply { mkdirs() }
            .let { File(it, "rec_${System.currentTimeMillis()}.m4a") }
}
