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

@Serializable
data class Pattern(
    val name: String,
    val timings: LongArray,
    val amplitudes: IntArray
) {
    fun toJson(): String = Json.encodeToString(this)

    /**
     * Joue ce pattern de vibration sur l'appareil.
     */
    fun play(context: Context) {
        if (timings.isEmpty()) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator == null || !vibrator.hasVibrator()) return

        vibrator.cancel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    companion object {
        private const val FILE_NAME = "patterns.json"

        /** Patterns par défaut injectés uniquement au premier lancement. */
        private val defaultPatterns = listOf(
            Pattern("Court", longArrayOf(0, 100), intArrayOf(0, 255)),
            Pattern("Double", longArrayOf(0, 100, 100, 100), intArrayOf(0, 255, 0, 255)),
            Pattern("Alternative", longArrayOf(0, 500, 50, 500, 500, 500), intArrayOf(0, 255, 0, 10, 0, 255))
        )

        fun fromJson(json: String): Pattern = Json.decodeFromString(json)

        /**
         * Sauvegarde la liste complète des patterns.
         */
        fun saveAll(context: Context, patterns: List<Pattern>) {
            try {
                val json = Json.encodeToString(patterns)
                context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                    it.write(json.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("Pattern", "Erreur sauvegarde", e)
            }
        }

        /**
         * Charge les patterns. Si le fichier n'existe pas, initialise avec les défauts.
         */
        fun loadAll(context: Context): List<Pattern> {
            val fileExists = context.getFileStreamPath(FILE_NAME).exists()
            
            if (!fileExists) {
                // Premier lancement : on initialise avec les patterns par défaut
                val initial = defaultPatterns.sortedBy { it.name }
                saveAll(context, initial)
                return initial
            }

            return try {
                val json = context.openFileInput(FILE_NAME).bufferedReader().readText()
                Json.decodeFromString<List<Pattern>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Pattern
        return name == other.name && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }
}
