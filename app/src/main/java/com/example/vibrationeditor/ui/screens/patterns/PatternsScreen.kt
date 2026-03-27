package com.example.vibrationeditor.ui.screens.patterns

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vibrationeditor.StableTopAppBar
import com.example.vibrationeditor.ui.screens.shared.Pattern

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Patterns screen listing available patterns and details.
 */
@Composable
fun PatternsScreen() {
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Test patterns
    val patterns = listOf(
        Pattern("Court", longArrayOf(0, 100), intArrayOf(0, 255)),
        Pattern("Double", longArrayOf(0, 100, 100, 100), intArrayOf(0, 255, 0, 255)),
        Pattern("Alternative", longArrayOf(0, 500, 50, 500, 500, 500), intArrayOf(0, 255, 0, 10, 0, 255))
    )

    Scaffold(topBar = { StableTopAppBar("Patterns") }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            // Search bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        onSearch = { expanded = false },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        placeholder = { Text("Rechercher...") },
                        leadingIcon = { Icon(Icons.Default.Search, "Recherche") },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer")
                                }
                            }
                        }
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                Text("Searched : $searchText")
            }

            // Patterns list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(patterns) { pattern ->
                    PatternCard(pattern = pattern)
                }
            }
        }
    }
}

/**
 * Create a card representing a pattern
 *
 * @param pattern Pattern.
 */
@Composable
fun PatternCard(pattern: Pattern) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pattern.name,
                fontSize = 16.sp
            )

            Button(onClick = {
                val vibrator = context.getSystemService(Vibrator::class.java)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8+ (API 26+)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    if (vibrator.hasAmplitudeControl()) {
                        // Amplitude
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1), audioAttributes)
                    } else {
                        // No amplitude
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, -1), audioAttributes)
                    }
                } else {
                    // Older versions
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }

            }) {
                Icon(
                    Icons.Default.PlayArrow,
                    "Lire"
                )
            }
        }
    }
}