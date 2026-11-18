package app.shout

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Live microphone -> speaker passthrough ("karaoke mic").
 *
 * Why AudioRecord + AudioTrack in low-latency mode: it is the lowest-latency path that
 * needs no native (C++) code. Smallest buffers the device allows, high-priority audio
 * thread, and the output uses the MEDIA stream so it goes to the Bluetooth speaker.
 *
 * Bluetooth itself still adds ~150-300 ms. That part is the speaker/codec, not the app.
 */
class Megaphone(context: Context) {
    @Volatile var volume = 1.0f       // 0..3 (up to 3x boost)
    @Volatile var echo = false
    /** When muted the mic keeps running but outputs silence (lets push-to-talk react instantly). */
    @Volatile var muted = false
    @Volatile private var running = false
    @Volatile private var generation = 0 // lets a quick stop+start never be cancelled by the old thread
    private var worker: Thread? = null

    private val sampleRate: Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    val isRunning get() = running

    @SuppressLint("MissingPermission") // permission is checked in the UI before start()
    fun start(onError: (String) -> Unit) {
        if (running) return
        running = true
        val gen = ++generation
        worker = thread(name = "Megaphone", priority = Thread.MAX_PRIORITY) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            var rec: AudioRecord? = null
            var track: AudioTrack? = null
            try {
                val inMin = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val outMin = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inMin * 2)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(outMin)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone unavailable")

                // Small chunks (~5 ms) keep the in-app delay low.
                val chunk = (sampleRate / 200).coerceAtLeast(64)
                val buf = ShortArray(chunk)
                // Echo: 280 ms delay line, 40% feedback.
                val delay = ShortArray((sampleRate * 0.28).roundToInt())
                var dPos = 0

                rec.startRecording()
                track.play()
                while (running && generation == gen) {
                    val n = rec.read(buf, 0, chunk)
                    if (n < 0) throw IllegalStateException("Microphone stopped working")
                    if (n == 0) continue
                    val v = if (muted) 0f else volume
                    val e = echo
                    for (i in 0 until n) {
                        var s = buf[i] * v
                        if (e) {
                            val d = delay[dPos].toFloat()
                            s += d * 0.5f
                            delay[dPos] = (s * 0.4f).toInt().coerceIn(-32768, 32767).toShort()
                            dPos = (dPos + 1) % delay.size
                        }
                        buf[i] = s.toInt().coerceIn(-32768, 32767).toShort()
                    }
                    track.write(buf, 0, n)
                }
            } catch (e: Exception) {
                if (generation == gen) onError(e.message ?: "Megaphone failed")
            } finally {
                if (generation == gen) running = false
                runCatching { rec?.stop() }; rec?.release()
                runCatching { track?.stop() }; track?.release()
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }
}
