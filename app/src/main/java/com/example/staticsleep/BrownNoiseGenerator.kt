package com.example.staticsleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class BrownNoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var audioThread: Thread? = null

    fun start() {
        if (isPlaying) return
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
            // Adjust buffer size per write iteration
            val buffer = ShortArray(4096)

            // Accumulator for Brown Noise filter
            var currentValue = 0.0f

            while (isPlaying) {
                for (i in buffer.indices) {
                    // 1. Generate White Noise (-1.0 to 1.0)
                    val white = (Math.random() * 2.0 - 1.0).toFloat()

                    // 2. Filter it to Brown Noise (Brownian motion / random walk)
                    // Dropping the coefficient from 0.02f to 0.008f heavily dampens the treble/hiss.
                    currentValue = (currentValue + (0.008f * white)) / 1.008f

                    // 3. Post-Filter Boost:
                    // Because low-pass filtering heavily reduces volume, we boost the low-end.
                    val volumeAdjustment = 7.5f // Brings up the bass volume safely
                    var sample = currentValue * volumeAdjustment

                    // 4. Hard-clamp to prevent digital distortion overflow
                    if (sample > 1.0f) sample = 1.0f
                    if (sample < -1.0f) sample = -1.0f

                    // 5. Convert float to 16-bit Signed Short PCM data
                    buffer[i] = (sample * 32767).toInt().toShort()
                }

                // Write data to the audio hardware stream
                if (audioTrack != null && isPlaying) {
                    audioTrack!!.write(buffer, 0, buffer.size)
                }
            }
        }

        audioThread!!.start()
    }

    fun stop() {
        isPlaying = false
        if (audioThread != null) {
            try {
                audioThread!!.join() // Wait for thread to finish loops safely
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            audioThread = null
        }

        if (audioTrack != null) {
            audioTrack!!.stop()
            audioTrack!!.release()
            audioTrack = null
        }
    }

    companion object {
        private const val SAMPLE_RATE = 44100
    }
}
