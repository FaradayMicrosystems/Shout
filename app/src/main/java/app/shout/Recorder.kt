package app.shout

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the phone mic to an .m4a (AAC) file. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set

    fun start(out: File) {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
    }

    /** Returns the finished file, or null if the recording was too short to be valid. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return try { r.stop(); file } catch (e: RuntimeException) { file?.delete(); null } finally { r.release() }
    }

    fun cancel() { stop()?.delete() }
}
