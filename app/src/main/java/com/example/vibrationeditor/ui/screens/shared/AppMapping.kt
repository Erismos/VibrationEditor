package com.example.vibrationeditor.ui.screens.shared

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistable mapping between an application and its notification channel vibration patterns.
 *
 * @property packageName Unique ID of the application.
 * @property channelMappings Map of channelId to the name of the custom vibration pattern.
 */
@Serializable
data class AppMapping(
    val packageName: String,
    val channelMappings: Map<String, String> = emptyMap()
) {
    companion object {
        private const val FILE_NAME = "app_mappings.json"

        /**
         * Loads all saved application mappings from internal storage.
         */
        fun loadAll(context: Context): Map<String, AppMapping> {
            return try {
                val json = context.openFileInput(FILE_NAME).bufferedReader().readText()
                val list: List<AppMapping> = Json.decodeFromString(json)
                list.associateBy { it.packageName }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /**
         * Saves the provided mappings to internal storage.
         */
        fun saveAll(context: Context, mappings: Map<String, AppMapping>) {
            try {
                val json = Json.encodeToString(mappings.values.toList())
                context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                    it.write(json.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
