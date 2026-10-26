package com.example.staticsleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

class StaticNoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var thread: Thread? = null
//    private var currentNoiseType = NoiseType.WHITE

    /**
     * Starts playing the specified noise type.
     * If noise is already playing, it dynamically switches the type without stopping the thread.
     */
    fun startStaticNoise() {
        if (isPlaying) return // Already running, type switch is handled dynamically below
        isPlaying = true

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        thread = Thread {
            val shortBuffer = ShortArray(bufferSize / 2)

            // Filter states for Brown noise
            var brownLastOut1 = 0.0f
            var brownLastOut2 = 0.0f

            while (isPlaying) {
                for (i in shortBuffer.indices) {
                    val white = Random.nextFloat() * 2.0f - 1.0f // Base white noise (-1.0 to 1.0)

                    // Stage 1: Initial frequency cut
                    brownLastOut1 = (brownLastOut1 + (0.015f * white)) / 1.015f

                    // Stage 2: Feed Stage 1's output into the second filter to eliminate leaks
                    brownLastOut2 = (brownLastOut2 + (0.015f * brownLastOut1)) / 1.015f

                    // Boost the volume because cascading filters reduces overall signal strength
                    val sampleValue: Float = brownLastOut2 * 12.0f

                    // Convert the floating-point sample (-1.0 to 1.0) safely to 16-bit PCM Short
                    val pcmSample = (sampleValue * Short.MAX_VALUE)
                        .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

                    shortBuffer[i] = pcmSample.toInt().toShort()
                }

                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            }
        }
        thread?.start()
    }

    /**
     * Stops the audio playback and safely kills the background thread.
     */
    fun stopStaticNoise() {
        isPlaying = false
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        thread = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
