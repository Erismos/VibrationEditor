package com.example.vibrationeditor.ui.screens.studio

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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

@Composable
fun Studio() {
    val context = LocalContext.current
    var pattern by remember {
        mutableStateOf(
            Pattern(
                "Custom Pattern",
                longArrayOf(200, 400, 200, 400),
                intArrayOf(100, 255, 150, 200)
            )
        )
    }

    Scaffold(topBar = { StableTopAppBar("Studio") }) { innerPadding ->
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
                text = "Tap a point to select, then drag to adjust intensity (vertical) and duration (horizontal).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            VibrationEnvelopeEditor(
                pattern = pattern,
                onPatternChange = { pattern = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pattern Configuration", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Duration: ${pattern.timings.sum()} ms")
                    Text("Segments: ${pattern.timings.size}")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val vibrator = context.getSystemService(Vibrator::class.java)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                if (vibrator.hasAmplitudeControl()) {
                                    vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1))
                                } else {
                                    vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, -1))
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(pattern.timings, -1)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Play Vibration")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            // Reset to a default pattern
                            pattern = Pattern(
                                "Custom Pattern",
                                longArrayOf(200, 400, 200, 400),
                                intArrayOf(100, 255, 150, 200)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Pattern")
                    }
                }
            }
        }
    }
}

@Composable
fun VibrationEnvelopeEditor(
    pattern: Pattern,
    onPatternChange: (Pattern) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val textMeasurer = rememberTextMeasurer()
    
    // Use a stable reference for the current pattern in the drag handler
    val currentPattern by rememberUpdatedState(pattern)
    val currentOnPatternChange by rememberUpdatedState(onPatternChange)

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        val horizontalPadding = 60f
        val verticalPadding = 70f
        val graphWidth = width - 2 * horizontalPadding
        val graphHeight = height - 2 * verticalPadding

        // We use the sum of timings as the time scale.
        // To avoid jitter when dragging, we could use a fixed scale, 
        // but here we allow it to expand.
        fun totalPatternTime(pattern: Pattern) = pattern.timings.sum().toFloat().coerceAtLeast(1000f)
        val maxAmplitude = 255f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->

                        var currentTime = 0f
                        var found = false
                        val p = currentPattern
                        val totalTime = totalPatternTime(p)
                        for (i in p.timings.indices) {
                            val segmentDuration = p.timings[i]
                            val x = horizontalPadding + (currentTime + segmentDuration / 2f) / totalTime * graphWidth
                            val y = height - verticalPadding - (p.amplitudes[i] / maxAmplitude) * graphHeight
                            
                            // Check if click is near a control point (using a generous 40dp-ish radius)
                            if ((offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y) < 1600f) {
                                selectedIndex = i
                                found = true
                                break
                            }
                            currentTime += segmentDuration
                        }
                        if (!found) selectedIndex = -1
                    }
                }
                .pointerInput(selectedIndex) {
                    if (selectedIndex != -1) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            
                            val p = currentPattern
                            val oldTotalTime = totalPatternTime(p)
                            val newTimings = p.timings.copyOf()
                            val newAmplitudes = p.amplitudes.copyOf()
                            
                            // Adjust amplitude (Y axis) - inverted because Y increases downwards
                            val deltaY = dragAmount.y
                            val amplitudeChange = -(deltaY / graphHeight * maxAmplitude).toInt()
                            newAmplitudes[selectedIndex] = (newAmplitudes[selectedIndex] + amplitudeChange).coerceIn(0, 255)
                            
                            // Adjust duration (X axis)
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

            // Draw X and Y axis lines
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
                
                // Segment background highlight if selected
                if (i == selectedIndex) {
                    drawRect(
                        color = Color.Blue.copy(alpha = 0.1f),
                        topLeft = Offset(xStart, verticalPadding),
                        size = androidx.compose.ui.geometry.Size(xEnd - xStart, graphHeight)
                    )
                }

                // Waveform line
                path.lineTo(xStart, y)
                path.lineTo(xEnd, y)
                
                // Draw control point circle in the middle of the segment
                val centerX = (xStart + xEnd) / 2f
                drawCircle(
                    color = if (i == selectedIndex) Color.Red else Color.Blue,
                    radius = if (i == selectedIndex) 10f else 6f,
                    center = Offset(centerX, y)
                )
                
                currentTime += duration
            }
            // Return to baseline at the end
            val lastX = horizontalPadding + (currentTime / totalTime) * graphWidth
            path.lineTo(lastX, height - verticalPadding)
            
            drawPath(path, Color.Blue, style = Stroke(width = 4f))
        }
    }
}
