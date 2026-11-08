package com.storyforge.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Original, locally synthesized ambient score. No downloaded loops or copyrighted samples.
 * A scene's atmosphere selects a restrained harmonic palette; the 12-second buffer loops.
 */
internal object AmbientScore {
    fun start(theme: String, effects: List<String>): AudioTrack? = runCatching {
        val sampleRate = 22050
        val seconds = 12
        val count = sampleRate * seconds
        val pcm = ShortArray(count)
        val mood = when {
            effects.any { it in setOf("blood_mist", "embers", "lightning") } || theme == "bloodmoon" -> 2
            effects.any { it in setOf("snow", "wind") } || theme == "frozen" -> 3
            effects.any { it in setOf("spores", "dense_fog") } || theme == "fungal" -> 1
            effects.any { it in setOf("rain", "heavy_rain") } -> 4
            else -> 0
        }
        val notes = when (mood) {
            1 -> doubleArrayOf(73.42, 110.0, 146.83, 220.0) // fungal, hollow and suspended
            2 -> doubleArrayOf(65.41, 98.0, 130.81, 196.0) // blood moon, uneasy minor
            3 -> doubleArrayOf(82.41, 123.47, 164.81, 246.94) // frozen, distant glass
            4 -> doubleArrayOf(55.0, 82.41, 110.0, 164.81) // rain, low warm drone
            else -> doubleArrayOf(58.27, 87.31, 116.54, 174.61) // dead forest
        }
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val slow = 0.82 + 0.12 * sin(2.0 * PI * t / 8.0)
            val swell = 0.76 + 0.24 * sin(2.0 * PI * t / 5.5 + 0.7)
            val drone = sin(2.0 * PI * notes[0] * t) * 0.30 +
                sin(2.0 * PI * notes[1] * t + 0.4) * 0.20 +
                sin(2.0 * PI * notes[2] * t + 1.1) * 0.13 +
                sin(2.0 * PI * notes[3] * t + 0.2) * 0.055
            val overtone = sin(2.0 * PI * (notes[2] * 2.0) * t + 0.6) * 0.025
            val edge = minOf(1.0, i.toDouble() / (sampleRate * 0.45), (count - i).toDouble() / (sampleRate * 0.45))
            val value = (drone + overtone) * slow * swell * edge * 0.17
            pcm[i] = (value.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(0.34f)
        track.play()
        track
    }.getOrNull()

    fun stop(track: AudioTrack?) {
        if (track == null) return
        runCatching { track.pause(); track.flush(); track.release() }
    }
}
