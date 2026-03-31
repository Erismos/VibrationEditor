package com.example.vibrationeditor.ui.screens.shared

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data class representing a vibration pattern.
 *
 * @property name Unique name of the pattern.
 * @property timings Array of durations for each segment in milliseconds.
 * @property amplitudes Array of intensities for each segment (0-255).
 */
@Serializable
data class Pattern(
    val name: String,
    val timings: LongArray,
    val amplitudes: IntArray
) {
    fun toJson(): String = Json.encodeToString(this)

    /**
     * Plays this vibration pattern on the device.
     */
    fun play(context: Context) {
        if (timings.isEmpty()) {
            Log.w("VibrationEditor", "Pattern '$name' is empty, skipping.")
            return
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            Log.e("VibrationEditor", "Device has no vibrator.")
            return
        }

        // Cancel any ongoing vibration to clear the way for this pattern
        vibrator.cancel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM) // High priority
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Adjust amplitudes array to match timings array length
            val safeAmplitudes = if (amplitudes.size >= timings.size) {
                amplitudes.sliceArray(timings.indices)
            } else {
                amplitudes + IntArray(timings.size - amplitudes.size) { 255 }
            }

            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, safeAmplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vibrator.vibrate(effect, audioAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    // Static methods
    companion object {
        fun fromJson(json: String): Pattern = Json.decodeFromString(json)

        fun saveAll(context: Context, patterns: List<Pattern>) {
            val json = Json.encodeToString(patterns)
            context.openFileOutput("patterns.json", Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        }

        fun loadAll(context: Context): List<Pattern> {
            return try {
                val json = context.openFileInput("patterns.json").bufferedReader().readText()
                Json.decodeFromString(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // Native methods
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pattern

        if (name != other.name) return false
        if (!timings.contentEquals(other.timings)) return false
        if (!amplitudes.contentEquals(other.amplitudes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }
}
