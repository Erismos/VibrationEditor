package com.example.vibrationeditor.ui.screens.studio

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vibrationeditor.StableTopAppBar
import com.example.vibrationeditor.ui.screens.shared.Pattern
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Alternative Studio screen with a touch-to-record interface inspired by iOS.
 * Includes a live visual waveform and a playback playhead.
 */
@Composable
fun StudioScreen2(
    patternToEdit: Pattern? = null,
    onPatternChange: (Pattern) -> Unit = {},
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onSwitchVersion: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()

    val defaultPattern = remember {
        Pattern("New Pattern", longArrayOf(), intArrayOf())
    }

    var pattern by remember { mutableStateOf(patternToEdit ?: defaultPattern) }
    var originalPattern by remember { mutableStateOf<Pattern?>(patternToEdit ?: defaultPattern) }

    // Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var currentTimestamp by remember { mutableLongStateOf(0L) }
    val maxRecordingTime = 10000L // 10 seconds max

    // Playback State
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    // Temporal storage for recording
    val recordedSegments = remember { mutableStateListOf<Pair<Long, Int>>() }
    var lastTransitionTime by remember { mutableLongStateOf(0L) }
    var isCurrentlyDown by remember { mutableStateOf(false) }

    // Persistence state
    var loadedPatternName by remember { mutableStateOf(patternToEdit?.name) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val hasModifications = remember(pattern, originalPattern) {
        originalPattern != null && pattern != originalPattern
    }

    // Sync local change back to global state
    LaunchedEffect(pattern) {
        onPatternChange(pattern)
    }

    LaunchedEffect(patternToEdit) {
        if (patternToEdit != null) {
            pattern = patternToEdit
            originalPattern = patternToEdit
            loadedPatternName = patternToEdit.name
        }
    }

    LaunchedEffect(hasModifications) {
        onDirtyStateChanged(hasModifications)
    }

    BackHandler(enabled = hasModifications) {
        onDismissDialog()
    }

    // Vibration feedback during recording
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        
        // Add the final segment
        val now = System.currentTimeMillis()
        val duration = now - lastTransitionTime
        if (duration > 0) {
            recordedSegments.add(duration to if (isCurrentlyDown) 255 else 0)
        }
        
        isRecording = false
        isCurrentlyDown = false
        vibrator.cancel()
        
        // Supprimer les segments de silence à la fin
        while (recordedSegments.isNotEmpty() && recordedSegments.last().second == 0) {
            recordedSegments.removeAt(recordedSegments.size - 1)
        }
        
        if (recordedSegments.isNotEmpty()) {
            val finalTimings = recordedSegments.map { it.first }.toLongArray()
            val finalAmplitudes = recordedSegments.map { it.second }.toIntArray()
            pattern = pattern.copy(timings = finalTimings, amplitudes = finalAmplitudes)
        }
    }

    fun startRecording() {
        recordedSegments.clear()
        // IMPORTANT: Always start with a 0 amplitude segment (silence)
        recordedSegments.add(0L to 0)
        
        recordingProgress = 0f
        currentTimestamp = System.currentTimeMillis()
        lastTransitionTime = currentTimestamp
        isRecording = true
        
        scope.launch {
            val start = System.currentTimeMillis()
            while (isRecording && System.currentTimeMillis() - start < maxRecordingTime) {
                val now = System.currentTimeMillis()
                currentTimestamp = now
                recordingProgress = (now - start).toFloat() / maxRecordingTime
                delay(16)
            }
            if (isRecording) stopRecording()
        }
    }

    fun handlePress() {
        if (!isRecording) startRecording()
        
        val now = System.currentTimeMillis()
        val duration = now - lastTransitionTime
        if (duration > 0 && isRecording) {
            // End the previous "OFF" segment
            recordedSegments.add(duration to 0)
        }
        
        isCurrentlyDown = true
        lastTransitionTime = now
        triggerVibration(context, 10000) 
    }

    fun handleRelease() {
        if (!isRecording) return
        
        val now = System.currentTimeMillis()
        val duration = now - lastTransitionTime
        if (duration > 0) {
            // End the previous "ON" segment
            recordedSegments.add(duration to 255)
        }
        
        isCurrentlyDown = false
        lastTransitionTime = now
        vibrator.cancel()
    }

    fun playPattern() {
        playbackJob?.cancel()
        pattern.play(context)
        playbackJob = scope.launch {
            isPlaying = true
            val total = pattern.timings.sum()
            val start = System.currentTimeMillis()
            while (isPlaying && System.currentTimeMillis() - start < total) {
                playbackProgress = (System.currentTimeMillis() - start).toFloat() / total
                delay(16)
            }
            isPlaying = false
            playbackProgress = 0f
        }
    }

    fun performSave(name: String) {
        try {
            val allPatterns = Pattern.loadAll(context).toMutableList()
            val index = allPatterns.indexOfFirst { it.name == name }
            val newPattern = pattern.copy(name = name)
            if (index != -1) allPatterns[index] = newPattern else allPatterns.add(newPattern)
            Pattern.saveAll(context, allPatterns)
            pattern = newPattern
            originalPattern = newPattern.copy()
            loadedPatternName = name
            scope.launch { snackbarHostState.showSnackbar("Pattern '$name' saved") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Error saving pattern") }
        }
    }

    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    Scaffold(
        topBar = { 
            StableTopAppBar(
                title = "Studio (Touch Mode)",
                action = {
                    IconButton(onClick = onSwitchVersion) {
                        Icon(Icons.Default.SwapHoriz, "Switch to Classic Mode")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Visualization",
                style = MaterialTheme.typography.titleMedium
            )
            
            // --- Waveform Area ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val displaySegments = if (isRecording) {
                        val ongoingDuration = currentTimestamp - lastTransitionTime
                        recordedSegments.toList() + listOf(ongoingDuration to if (isCurrentlyDown) 255 else 0)
                    } else {
                        pattern.timings.zip(pattern.amplitudes.toTypedArray()).map { it.first to it.second }
                    }

                    val waveformColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    val playheadColor = MaterialTheme.colorScheme.error
                    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                        val totalDuration = if (isRecording) maxRecordingTime else pattern.timings.sum().coerceAtLeast(1000L)
                        
                        // --- Time Scale (Ticks & Labels) ---
                        val step = if (totalDuration > 5000L) 1000L else 500L
                        for (time in 0..totalDuration step step) {
                            val x = (time.toFloat() / totalDuration) * size.width
                            drawLine(
                                color = tickColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            
                            // Labels every 1s (or 500ms if short)
                            if (time % 1000L == 0L || totalDuration <= 2000L) {
                                val label = "${time / 1000f}s"
                                val textLayoutResult = textMeasurer.measure(label, style = labelStyle)
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(x - textLayoutResult.size.width / 2, size.height - textLayoutResult.size.height)
                                )
                            }
                        }

                        // --- Waveform ---
                        val path = Path()
                        var currentX = 0f
                        path.moveTo(0f, size.height - 20.dp.toPx()) // Leave space for labels
                        
                        displaySegments.forEach { (duration, amplitude) ->
                            val segmentWidth = (duration.toFloat() / totalDuration) * size.width
                            // Scale amplitude to height, leaving space for labels at the bottom
                            val availableHeight = size.height - 20.dp.toPx()
                            val y = availableHeight - (amplitude.toFloat() / 255f) * availableHeight
                            
                            path.lineTo(currentX, y)
                            path.lineTo(currentX + segmentWidth, y)
                            currentX += segmentWidth
                        }
                        
                        path.lineTo(currentX, size.height - 20.dp.toPx())
                        
                        drawPath(
                            path = path,
                            color = waveformColor,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // --- Playhead ---
                        if (isPlaying) {
                            val playheadX = playbackProgress * size.width
                            drawLine(
                                color = playheadColor,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, size.height - 20.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    if (isRecording) {
                        CircularProgressIndicator(
                            progress = { recordingProgress },
                            modifier = Modifier.size(60.dp),
                            strokeWidth = 4.dp
                        )
                    } else if (pattern.timings.isEmpty()) {
                        Text("No pattern", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Recording Pad",
                style = MaterialTheme.typography.titleMedium
            )

            // --- Recording Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = if (isCurrentlyDown) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                handlePress()
                                tryAwaitRelease()
                                handleRelease()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentlyDown) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "VIBRATING",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text("Press and Hold to Record", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (isRecording) {
                Button(
                    onClick = { stopRecording() },
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Recording")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Config", style = MaterialTheme.typography.titleMedium)
                        if (loadedPatternName != null) {
                            Text("Current: $loadedPatternName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Duration: ${pattern.timings.sum()} ms")
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { playPattern() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pattern.timings.isNotEmpty() && !isPlaying
                        ) {
                            Icon(if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPlaying) "Playing..." else "Play Result")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showLoadDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Load", fontSize = 14.sp)
                            }
                            Button(
                                onClick = { loadedPatternName?.let { performSave(it) } },
                                enabled = loadedPatternName != null && hasModifications,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save", fontSize = 14.sp)
                            }
                            Button(onClick = { 
                                saveName = loadedPatternName ?: ""
                                showSaveAsDialog = true 
                            }, modifier = Modifier.weight(1f)) {
                                Text("Save As", fontSize = 14.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // --- Dialogs ---
        if (showSaveAsDialog) {
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = false },
                title = { Text("Save Pattern") },
                text = { TextField(value = saveName, onValueChange = { saveName = it }, label = { Text("Name") }, singleLine = true) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (Pattern.loadAll(context).any { it.name == saveName }) showOverwriteConfirm = true 
                            else { performSave(saveName); showSaveAsDialog = false }
                        },
                        enabled = saveName.isNotBlank()
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") } }
            )
        }

        if (showOverwriteConfirm) {
            AlertDialog(
                onDismissRequest = { showOverwriteConfirm = false },
                title = { Text("Overwrite?") },
                text = { Text("A pattern named '$saveName' already exists.") },
                confirmButton = {
                    Button(onClick = { performSave(saveName); showOverwriteConfirm = false; showSaveAsDialog = false }) { Text("Overwrite") }
                },
                dismissButton = { TextButton(onClick = { showOverwriteConfirm = false }) { Text("Cancel") } }
            )
        }

        if (showLoadDialog) {
            val allPatterns = Pattern.loadAll(context)
            AlertDialog(
                onDismissRequest = { showLoadDialog = false },
                title = { Text("Load Pattern") },
                text = {
                    if (allPatterns.isEmpty()) Text("No patterns found.") else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(allPatterns) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = { Text("${p.timings.sum()}ms") },
                                    modifier = Modifier.clickable {
                                        pattern = p.copy(); originalPattern = p.copy(); loadedPatternName = p.name
                                        showLoadDialog = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showLoadDialog = false }) { Text("Close") } }
            )
        }
    }
}

/** Simple vibration trigger helper for recording feedback. */
private fun triggerVibration(context: Context, duration: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, 255))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}
