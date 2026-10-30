package com.example.staticsleep

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.media.AudioFocusRequest

class BrownNoiseGenerator(context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var wasInterrupted = false
    private var audioThread: Thread? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val random = java.util.Random()

    // This listener triggers whenever an alarm, phone call, or video takes focus
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            // The alarm rings: pause the engine but DO NOT abandon focus yet
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) {
                    wasInterrupted = true
                    pausePlaybackOnly() // Clear the thread/track without destroying the focus slot
                }
            }

            // Another app took over permanently (e.g. user started Spotify or a YouTube video)
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasInterrupted = false
                stop() // Full cleanup
            }

            // The alarm is dismissed: if we were interrupted, wake back up!
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasInterrupted) {
                    wasInterrupted = false
                    start()
                }
            }
        }
    }

    fun start() {
        if (isPlaying) return

        // Request Audio Focus before starting the stream
        val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

        val focusGranted = audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        // Only play if the system says it's okay
        if (!focusGranted) return

        isPlaying = true

        // Initialize AudioTrack based on Android Version
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2 // Double buffer to ensure smoothness

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack!!.play()

        // Run the synthesis on a background thread to prevent UI stutter
        audioThread = Thread {
            // Elevate thread priority immediately so the OS doesn't starve the audio stream
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

            // Adjust buffer size per write iteration
            val buffer = ShortArray(4096)

            // Accumulator for Brown Noise filter
            var currentValue = 0.0f

            // Pre-declare primitive loop variables to avoid any stack allocation pressure
            var white: Float
            var sample: Float

            while (isPlaying) {
                for (i in buffer.indices) {
                    // Fast, allocation-free white noise generation
                    white = random.nextFloat() * 2.0f - 1.0f

                    // Brown noise leaky filter
                    currentValue = (currentValue + (0.008f * white)) / 1.008f

                    // Boost & Hard-clamp
                    sample = currentValue * 7.5f
                    if (sample > 1.0f) sample = 1.0f
                    else if (sample < -1.0f) sample = -1.0f

                    // Convert to 16-bit PCM Short
                    buffer[i] = (sample * 32767f).toInt().toShort()
                }

                // Non-blocking check and write
                val track = audioTrack
                if (track != null && isPlaying && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(buffer, 0, buffer.size)
                }
            }
        }.apply {
            name = "BrownNoiseAudioThread"
        }

        audioThread!!.start()
    }

    private fun pausePlaybackOnly() {
        isPlaying = false

        // Flush the audio track buffer to unblock the thread loop instantly
        audioTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.flush()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Safely tear down the thread
        audioThread?.let { thread ->
            try { thread.join(200) } catch (e: InterruptedException) { e.printStackTrace() }
            audioThread = null
        }

        // Tear down track hardware so it doesn't hold system memory resources open
        audioTrack?.let { track ->
            try { if (track.state == AudioTrack.STATE_INITIALIZED) track.release() } catch (e: Exception) { e.printStackTrace() }
            audioTrack = null
        }
    }

    fun stop() {
        wasInterrupted = false // Make sure an explicit user stop resets this flag

        // Use our helper to cleanly dismantle the audio path
        pausePlaybackOnly()

        // Abandon Audio Focus completely since the user explicitly wanted out
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    companion object {
        private const val SAMPLE_RATE = 44100
    }
}