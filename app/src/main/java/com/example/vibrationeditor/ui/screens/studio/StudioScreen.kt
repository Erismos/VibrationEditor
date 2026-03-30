package com.example.vibrationeditor.ui.screens.studio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
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
import kotlinx.coroutines.launch

@Composable
fun Studio(
    onDirtyStateChanged: (Boolean) -> Unit = {},
    showUnsavedDialogTrigger: Boolean = false,
    onDismissDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultPattern = remember {
        Pattern(
            "Custom Pattern",
            longArrayOf(200, 400, 200, 400),
            intArrayOf(100, 255, 150, 200)
        )
    }

    var pattern by remember { mutableStateOf(defaultPattern) }
    var originalPattern by remember { mutableStateOf<Pattern?>(defaultPattern) }

    var isAddingPoint by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    // Persistence state
    var loadedPatternName by remember { mutableStateOf<String?>(null) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val hasModifications = remember(pattern, originalPattern) {
        originalPattern != null && pattern != originalPattern
    }

    // Sync with parent
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
        topBar = { StableTopAppBar("Studio") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
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

            VibrationEnvelopeEditor(
                pattern = pattern,
                onPatternChange = { pattern = it },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { selectedIndex = it },
                isAddingPoint = isAddingPoint,
                onPointAdded = { isAddingPoint = false },
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
                    
                    // Row for persistence: Load, Save, Save As
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showLoadDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }

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
                            Text("Save As")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Final row for Play
                    Button(
                        onClick = {
                            pattern.play(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Play")
                    }
                }
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
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentPattern by rememberUpdatedState(pattern)
    val currentOnPatternChange by rememberUpdatedState(onPatternChange)

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
                            for (i in p.timings.indices) {
                                val segmentDuration = p.timings[i]
                                val x = horizontalPadding + (currentTime + segmentDuration / 2f) / totalTime * graphWidth
                                val y = height - verticalPadding - (p.amplitudes[i] / maxAmplitude) * graphHeight

                                if ((offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y) < 1600f) {
                                    onSelectedIndexChange(i)
                                    found = true
                                    break
                                }
                                currentTime += segmentDuration
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
                            newAmplitudes[selectedIndex] = (newAmplitudes[selectedIndex] + amplitudeChange).coerceIn(0, 255)
                            
                            val deltaX = dragAmount.x
                            val durationChange = (deltaX / graphWidth * oldTotalTime).toLong()
                            newTimings[selectedIndex] = (newTimings[selectedIndex] + durationChange).coerceAtLeast(10L)
                            
                            currentOnPatternChange(p.copy(timings = newTimings, amplitudes = newAmplitudes))
                        }
                    }
                }
        ) {
            // Draw Timeline (top)
            val timeStep = 200 // ms
            val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
            val totalTime = totalPatternTime(pattern)

            for (t in 0..totalTime.toInt() step timeStep) {
                val x = horizontalPadding + (t / totalTime) * graphWidth
                if (x > width - horizontalPadding + 1) continue

                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(x, verticalPadding),
                    end = Offset(x, height - verticalPadding),
                    strokeWidth = 1f
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "${t}ms",
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
                    color = Color.LightGray.copy(alpha = 0.3f),
                    start = Offset(horizontalPadding, y),
                    end = Offset(width - horizontalPadding, y),
                    strokeWidth = 1f
                )
            }

            // Draw X and Y origin lines
            drawLine(Color.DarkGray, Offset(horizontalPadding, height - verticalPadding), Offset(width - horizontalPadding, height - verticalPadding), 2f)
            drawLine(Color.DarkGray, Offset(horizontalPadding, verticalPadding), Offset(horizontalPadding, height - verticalPadding), 2f)

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
                        color = Color.Blue.copy(alpha = 0.1f),
                        topLeft = Offset(xStart, verticalPadding),
                        size = androidx.compose.ui.geometry.Size(xEnd - xStart, graphHeight)
                    )
                }

                path.lineTo(xStart, y)
                path.lineTo(xEnd, y)

                drawCircle(
                    color = if (i == selectedIndex) Color.Red else Color.Blue,
                    radius = if (i == selectedIndex) 10f else 6f,
                    center = Offset((xStart + xEnd) / 2f, y)
                )

                currentTime += duration
            }
            path.lineTo(horizontalPadding + (currentTime / totalTime) * graphWidth, height - verticalPadding)
            drawPath(path, Color.Blue, style = Stroke(4f))
        }
    }
}

fun LongArray.toMutableList(): MutableList<Long> = this.toList().toMutableList()
fun IntArray.toMutableList(): MutableList<Int> = this.toList().toMutableList()

// do not remove this todo section TODO next nice additions:
//Import and links from the other tabs
//- Zoom and adaptive time text
// - A bar that moves when playing
// - notifications about unsupported stuff
//   - Other windows of types of vibration edits