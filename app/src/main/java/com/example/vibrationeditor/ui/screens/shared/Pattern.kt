package com.example.vibrationeditor.ui.screens.shared

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Pattern(
    val name: String,
    val timings: LongArray,
    val amplitudes: IntArray
) {
    fun toJson(): String = Json.encodeToString(this)

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

    fun playPattern(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ (API 26+)
            if (vibrator.hasAmplitudeControl()) {
                // Amplitude
                vibrator.vibrate(VibrationEffect.createWaveform(this.timings, this.amplitudes, -1))
            } else {
                // No amplitude
                vibrator.vibrate(VibrationEffect.createWaveform(this.timings, -1))
            }
        } else {
            // Older versions
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
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
