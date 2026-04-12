package com.example.vibrationeditor.ui.screens.studio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vibrationeditor.StableTopAppBar
import com.example.vibrationeditor.ui.screens.shared.Pattern
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun Studio(
    patternToEdit: Pattern? = null,
    initialOriginalPattern: Pattern? = null,
    onPatternChange: (Pattern) -> Unit = {},
    onOriginalPatternChange: (Pattern) -> Unit = {},
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onSwitchVersion: (Pattern) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val defaultPattern = remember {
        Pattern(
            "Custom Pattern",
            longArrayOf(10),
            intArrayOf(0)
        )
    }

    var pattern by remember { mutableStateOf(patternToEdit ?: defaultPattern) }
    var originalPattern by remember { mutableStateOf<Pattern?>(initialOriginalPattern ?: patternToEdit ?: defaultPattern) }

    var isAddingPoint by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    
    // Persistence state
    var loadedPatternName by remember { mutableStateOf(patternToEdit?.name) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    // Sync only original pattern changes (happens on load/save)
    LaunchedEffect(originalPattern) {
        originalPattern?.let { onOriginalPatternChange(it) }
    }

    // Update state ONLY if patternToEdit changes from the outside (e.g. Navigated from Patterns list)
    LaunchedEffect(patternToEdit) {
        if (patternToEdit != null && patternToEdit != pattern) {
            pattern = patternToEdit
            loadedPatternName = patternToEdit.name
        }
    }

    // Also update original if initialOriginalPattern changes from outside
    LaunchedEffect(initialOriginalPattern) {
        if (initialOriginalPattern != null && initialOriginalPattern != originalPattern) {
            originalPattern = initialOriginalPattern
        }
    }

    val hasModifications = remember(pattern, originalPattern) {
        originalPattern != null && pattern != originalPattern
    }

    // Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val content = pattern.timings.indices.joinToString("\n") { i ->
                            "${pattern.timings[i]},${pattern.amplitudes[i]}"
                        }
                        outputStream.write(content.toByteArray())
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar("Pattern exported successfully")
                    }
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Error exporting pattern")
                    }
                }
            }
        }
    )

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        val lines = reader.readLines()
                        val timings = mutableListOf<Long>()
                        val amplitudes = mutableListOf<Int>()
                        
                        lines.forEach { line ->
                            val parts = line.split(",")
                            if (parts.size == 2) {
                                try {
                                    timings.add(parts[0].trim().toLong())
                                    amplitudes.add(parts[1].trim().toInt())
                                } catch (e: NumberFormatException) {
                                    // Skip invalid lines
                                }
                            }
                        }
                        
                        if (timings.isNotEmpty()) {
                            val newPattern = Pattern(
                                name = "Imported Pattern",
                                timings = timings.toLongArray(),
                                amplitudes = amplitudes.toIntArray()
                            )
                            pattern = newPattern
                            originalPattern = null // Treat as unsaved
                            loadedPatternName = null
                            selectedIndex = -1
                            onPatternChange(newPattern)
                            scope.launch {
                                snackbarHostState.showSnackbar("Pattern imported successfully")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("No valid vibration data found in file")
                            }
                        }
                    }
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Error importing pattern")
                    }
                }
            }
        }
    )

    // Sync dirty state with parent (Efficient: only runs when hasModifications flips)
    LaunchedEffect(hasModifications) {
        onDirtyStateChanged(hasModifications)
    }

    // Handle system back press
    BackHandler(enabled = hasModifications) {
        onDismissDialog() // Trigger the global dialog
    }

    // Helper functions for saving
    fun performSave(name: String) {
        try {
            val allPatterns = Pattern.loadAll(context).toMutableList()
            val index = allPatterns.indexOfFirst { it.name == name }
            val newPattern = pattern.copy(name = name)
            if (index != -1) {
                allPatterns[index] = newPattern
            } else {
                allPatterns.add(newPattern)
            }
            Pattern.saveAll(context, allPatterns)
            
            pattern = newPattern
            originalPattern = newPattern.copy()
            loadedPatternName = name
            onPatternChange(newPattern)
            
            scope.launch {
                snackbarHostState.showSnackbar("Pattern '$name' saved")
            }
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Error saving pattern")
            }
        }
    }

    Scaffold(
        topBar = { 
            StableTopAppBar(
                title = "Studio (Classic)",
                action = {
                    IconButton(onClick = { onSwitchVersion(pattern) }){
                        Icon(Icons.Default.SwapHoriz, "Switch to Touch Mode")
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
                .padding(16.dp)
        ) {
            // Fixed Top Part
            Text(
                text = "Vibration Envelope Editor",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = if (isAddingPoint) "Click on the graph to add a new segment." else "Tap a point to select, then drag to adjust.",
                style = MaterialTheme.typography.bodySmall,
                color = if (isAddingPoint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable Bottom Part
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                VibrationEnvelopeEditor(
                    pattern = pattern,
                    onPatternChange = { pattern = it },
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    isAddingPoint = isAddingPoint,
                    onPointAdded = { isAddingPoint = false },
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tool Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isAddingPoint = !isAddingPoint },
                        modifier = Modifier.weight(1f),
                        colors = if (isAddingPoint) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isAddingPoint) "Cancel" else "Add Point")
                    }

                    Button(
                        onClick = {
                            if (selectedIndex != -1) {
                                val newTimings = pattern.timings.toMutableList()
                                val newAmplitudes = pattern.amplitudes.toMutableList()
                                newTimings.removeAt(selectedIndex)
                                newAmplitudes.removeAt(selectedIndex)
                                pattern = pattern.copy(
                                    timings = newTimings.toLongArray(),
                                    amplitudes = newAmplitudes.toIntArray()
                                )
                                selectedIndex = -1
                            }
                        },
                        enabled = selectedIndex != -1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Point")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pattern Configuration", style = MaterialTheme.typography.titleMedium)
                        if (loadedPatternName != null) {
                            Row {
                                Text("Current: $loadedPatternName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                if (hasModifications) {
                                    Text(" (modified)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Duration: ${pattern.timings.sum()} ms")
                        Text("Segments: ${pattern.timings.size}")

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play/Stop Button
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    playbackJob?.cancel()
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val manager = context.getSystemService(android.os.VibratorManager::class.java)
                                        manager?.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        context.getSystemService(android.os.Vibrator::class.java)
                                    }
                                    vibrator?.cancel()
                                    isPlaying = false
                                    playbackProgress = 0f
                                } else {
                                    val totalDuration = pattern.timings.sum()
                                    if (totalDuration > 0) {
                                        isPlaying = true
                                        pattern.play(context)
                                        playbackJob = scope.launch {
                                            val start = System.currentTimeMillis()
                                            while (isPlaying && System.currentTimeMillis() - start < totalDuration) {
                                                playbackProgress = (System.currentTimeMillis() - start).toFloat() / totalDuration
                                                delay(16)
                                            }
                                            isPlaying = false
                                            playbackProgress = 0f
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isPlaying) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) {
                            Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPlaying) "Stop" else "Play")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Row 1: Load, Import, Export
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showLoadDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.width(2.dp))
                                Text("Load", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("text/plain")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Spacer(Modifier.width(2.dp))
                                Text("Import", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { exportLauncher.launch("${loadedPatternName ?: "vibration"}.txt") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(Modifier.width(2.dp))
                                Text("Export", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2: Save, Save As
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { loadedPatternName?.let { performSave(it) } },
                                enabled = loadedPatternName != null && hasModifications,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }

                            Button(
                                onClick = { 
                                    saveName = loadedPatternName ?: ""
                                    showSaveAsDialog = true 
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SaveAs, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save As")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                    }
                }
                // Extra spacer to ensure we can scroll past the bottom card
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // --- Dialogs ---

        if (showSaveAsDialog) {
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = false },
                title = { Text("Save Pattern As") },
                text = {
                    TextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Pattern Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val exists = Pattern.loadAll(context).any { it.name == saveName }
                            if (exists) {
                                showOverwriteConfirm = true
                            } else {
                                performSave(saveName)
                                showSaveAsDialog = false
                            }
                        },
                        enabled = saveName.isNotBlank()
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showOverwriteConfirm) {
            AlertDialog(
                onDismissRequest = { showOverwriteConfirm = false },
                title = { Text("Overwrite Pattern?") },
                text = { Text("A pattern named '$saveName' already exists. Do you want to overwrite it?") },
                confirmButton = {
                    Button(
                        onClick = {
                            performSave(saveName)
                            showOverwriteConfirm = false
                            showSaveAsDialog = false
                        }
                    ) { Text("Overwrite") }
                },
                dismissButton = {
                    TextButton(onClick = { showOverwriteConfirm = false }) { Text("Cancel") }
                }
            )
        }

        if (showLoadDialog) {
            val allPatterns = Pattern.loadAll(context)
            AlertDialog(
                onDismissRequest = { showLoadDialog = false },
                title = { Text("Load Pattern") },
                text = {
                    if (allPatterns.isEmpty()) {
                        Text("No saved patterns found.")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(allPatterns) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = { Text("${p.timings.sum()}ms - ${p.timings.size} segments") },
                                    modifier = Modifier.clickable {
                                        pattern = p.copy()
                                        originalPattern = p.copy()
                                        loadedPatternName = p.name
                                        selectedIndex = -1
                                        onPatternChange(p)
                                        showLoadDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Pattern '${p.name}' loaded")
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLoadDialog = false }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
fun VibrationEnvelopeEditor(
    pattern: Pattern,
    onPatternChange: (Pattern) -> Unit,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    isAddingPoint: Boolean,
    onPointAdded: () -> Unit,
    isPlaying: Boolean,
    playbackProgress: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentPattern by rememberUpdatedState(pattern)
    val currentOnPatternChange by rememberUpdatedState(onPatternChange)
    
    // UI Colors based on theme
    val curveColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelStyle = TextStyle(
        fontSize = 10.sp, 
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val horizontalPadding = 60f
        val verticalPadding = 70f
        val graphWidth = width - 2 * horizontalPadding
        val graphHeight = height - 2 * verticalPadding

        fun totalPatternTime(pattern: Pattern) = pattern.timings.sum().toFloat().coerceAtLeast(1000f)
        val maxAmplitude = 255f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Combined gesture detection
                .pointerInput(isAddingPoint) {
                    detectTapGestures { offset ->
                        val p = currentPattern
                        val totalTime = totalPatternTime(p)

                        if (isAddingPoint) {
                            val clickedTime = ((offset.x - horizontalPadding) / graphWidth * totalTime).toLong().coerceAtLeast(0)
                            val clickedAmplitude = ((height - verticalPadding - offset.y) / graphHeight * maxAmplitude).toInt().coerceIn(0, 255)
                            
                            var accumulatedTime = 0L
                            var insertIndex = p.timings.size
                            for (i in p.timings.indices) {
                                if (accumulatedTime + p.timings[i] > clickedTime) {
                                    insertIndex = i
                                    break
                                }
                                accumulatedTime += p.timings[i]
                            }

                            val newTimings = p.timings.toMutableList()
                            val newAmplitudes = p.amplitudes.toMutableList()

                            if (insertIndex < p.timings.size) {
                                val originalDuration = p.timings[insertIndex]
                                val timeIntoSegment = clickedTime - accumulatedTime

                                if (timeIntoSegment > 10) {
                                    newTimings[insertIndex] = timeIntoSegment
                                    newTimings.add(insertIndex + 1, 200L)
                                    newAmplitudes.add(insertIndex + 1, clickedAmplitude)

                                    val remaining = originalDuration - timeIntoSegment
                                    if (remaining > 10) {
                                        newTimings.add(insertIndex + 2, remaining)
                                        newAmplitudes.add(insertIndex + 2, p.amplitudes[insertIndex])
                                    }
                                } else {
                                    newTimings.add(insertIndex, 200L)
                                    newAmplitudes.add(insertIndex, clickedAmplitude)
                                }
                            } else {
                                newTimings.add(200L)
                                newAmplitudes.add(clickedAmplitude)
                            }

                            currentOnPatternChange(p.copy(timings = newTimings.toLongArray(), amplitudes = newAmplitudes.toIntArray()))
                            onPointAdded()
                        } else {
                            var currentTime = 0f
                            var found = false
                            //first pass to look for nearby points
                            for (i in p.timings.indices) {
                                val segmentDuration = p.timings[i]
                                val x = horizontalPadding + (currentTime + segmentDuration / 2f) / totalTime * graphWidth
                                val y = height - verticalPadding - (p.amplitudes[i] / maxAmplitude) * graphHeight

                                if ((offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y) < 800f) {
                                    onSelectedIndexChange(i)
                                    found = true
                                    break
                                }
                                currentTime += segmentDuration
                            }
                            //if no point selected, second pass for zone
                            if (!found) {
                                currentTime = 0f
                                for (i in p.timings.indices) {
                                    val segmentDuration = p.timings[i]
                                    val x =
                                        horizontalPadding + (currentTime + segmentDuration / 2f) / totalTime * graphWidth
                                    val y =
                                        height - verticalPadding - (p.amplitudes[i] / maxAmplitude) * graphHeight

                                    if ((offset.x - x) < segmentDuration/2f/totalTime*graphWidth) {
                                        onSelectedIndexChange(i)
                                        found = true
                                        break
                                    }
                                    currentTime += segmentDuration
                                }
                            }


                            if (!found) onSelectedIndexChange(-1)
                        }
                    }
                }
                .pointerInput(selectedIndex) {
                    if (selectedIndex != -1 && !isAddingPoint) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()

                            val p = currentPattern
                            val oldTotalTime = totalPatternTime(p)
                            val newTimings = p.timings.copyOf()
                            val newAmplitudes = p.amplitudes.copyOf()

                            val deltaY = dragAmount.y
                            val amplitudeChange = -(deltaY / graphHeight * maxAmplitude).toInt()
                            newAmplitudes[selectedIndex] =
                                (newAmplitudes[selectedIndex] + amplitudeChange).coerceIn(0, 255)

                            val deltaX = dragAmount.x
                            val durationChange = (deltaX / graphWidth * oldTotalTime).toLong()
                            newTimings[selectedIndex] =
                                (newTimings[selectedIndex] + durationChange).coerceAtLeast(10L)

                            currentOnPatternChange(
                                p.copy(timings = newTimings, amplitudes = newAmplitudes)
                            )
                        }
                    }
                }
        ) {
            val totalTime = totalPatternTime(pattern)
            
            // Adaptive time stepping
            val targetDivisions = 8
            val rawStep = totalTime / targetDivisions
            val niceSteps = listOf(100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f, 50000f)
            val timeStep = niceSteps.firstOrNull { it >= rawStep } ?: niceSteps.last()

            for (t in 0..totalTime.toInt() step timeStep.toInt()) {
                val x = horizontalPadding + (t / totalTime) * graphWidth
                if (x > width - horizontalPadding + 1) continue

                drawLine(
                    color = gridColor,
                    start = Offset(x, verticalPadding),
                    end = Offset(x, height - verticalPadding),
                    strokeWidth = 1f
                )

                // Convert to seconds for labels
                val secondsText = String.format("%.1fs", t / 1000f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = secondsText,
                    topLeft = Offset(x - 20f, verticalPadding - 35f),
                    style = labelStyle
                )
            }

            // Draw Y-axis labels
            val amplitudesToDraw = listOf(0, 128, 255)
            for (amp in amplitudesToDraw) {
                val y = height - verticalPadding - (amp / maxAmplitude) * graphHeight
                drawText(
                    textMeasurer = textMeasurer,
                    text = amp.toString(),
                    topLeft = Offset(horizontalPadding - 45f, y - 15f),
                    style = labelStyle
                )
                drawLine(
                    color = gridColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(width - horizontalPadding, y),
                    strokeWidth = 1f
                )
            }

            // Draw X and Y origin lines
            drawLine(axisColor, Offset(horizontalPadding, height - verticalPadding), Offset(width - horizontalPadding, height - verticalPadding), 2f)
            drawLine(axisColor, Offset(horizontalPadding, verticalPadding), Offset(horizontalPadding, height - verticalPadding), 2f)

            // Draw the waveform path
            val path = Path()
            var currentTime = 0f
            path.moveTo(horizontalPadding, height - verticalPadding)
            for (i in pattern.timings.indices) {
                val duration = pattern.timings[i]
                val amplitude = pattern.amplitudes[i]
                val xStart = horizontalPadding + (currentTime / totalTime) * graphWidth
                val xEnd = horizontalPadding + ((currentTime + duration) / totalTime) * graphWidth
                val y = height - verticalPadding - (amplitude / maxAmplitude) * graphHeight

                if (i == selectedIndex) {
                    drawRect(
                        color = curveColor.copy(alpha = 0.15f),
                        topLeft = Offset(xStart, verticalPadding),
                        size = androidx.compose.ui.geometry.Size(xEnd - xStart, graphHeight)
                    )
                }

                path.lineTo(xStart, y)
                path.lineTo(xEnd, y)

                drawCircle(
                    color = if (i == selectedIndex) Color.Red else curveColor,
                    radius = if (i == selectedIndex) 10f else 6f,
                    center = Offset((xStart + xEnd) / 2f, y)
                )

                currentTime += duration
            }
            path.lineTo(horizontalPadding + (currentTime / totalTime) * graphWidth, height - verticalPadding)
            drawPath(path, curveColor, style = Stroke(5f))

            // --- Playhead ---
            if (isPlaying) {
                val playheadX = horizontalPadding + (playbackProgress * graphWidth)
                drawLine(
                    color = playheadColor,
                    start = Offset(playheadX, verticalPadding),
                    end = Offset(playheadX, height - verticalPadding),
                    strokeWidth = 2.dp.toPx()
                )
            }

            //Draw the text of selected index
            if(selectedIndex != -1) {
                currentTime = 0f
                for (i in pattern.timings.indices) {
                    if (i == selectedIndex) {
                        val duration = pattern.timings[i]
                        val amplitude = pattern.amplitudes[i]
                        val xStart = horizontalPadding + (currentTime / totalTime) * graphWidth
                        val xEnd =
                            horizontalPadding + ((currentTime + duration) / totalTime) * graphWidth
                        val y = height - verticalPadding - (amplitude / maxAmplitude) * graphHeight

                        // Display current values above the selected point
                        val valueText = "${duration}ms, $amplitude"
                        val textLayoutResult = textMeasurer.measure(
                            valueText,
                            style = labelStyle.copy(
                                color = curveColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                        val centerX = (xStart + xEnd) / 2f
                        drawText(
                            textMeasurer = textMeasurer,
                            text = valueText,
                            topLeft = Offset(centerX - textLayoutResult.size.width / 2f, y - 40f),
                            style = labelStyle.copy(
                                color = Color.Red,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                    }
                    currentTime += pattern.timings[i]
                }
            }
        }
    }
}

fun LongArray.toMutableList(): MutableList<Long> = this.toList().toMutableList()
fun IntArray.toMutableList(): MutableList<Int> = this.toList().toMutableList()

// do not remove this todo section TODO next nice additions:
// - A bar that moves when playing
// - ***notifications about unsupported stuff
//   - Other windows of types of vibration edits
// - **intensity + duration number above selected point
