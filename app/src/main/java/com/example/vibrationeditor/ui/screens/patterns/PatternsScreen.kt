package com.example.vibrationeditor.ui.screens.patterns

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vibrationeditor.AppDestinations
import com.example.vibrationeditor.ui.screens.shared.Pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Patterns screen listing available patterns and details.
 *
 * @param navigateTo Callback to navigate between screens.
 * @param onEditPattern Callback to start editing a specific pattern in the Studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternsScreen(
    navigateTo: (AppDestinations) -> Unit,
    onEditPattern: (Pattern) -> Unit
) {
    val context = LocalContext.current
    var allPatterns by remember { mutableStateOf<List<Pattern>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var selectedPattern by remember { mutableStateOf<Pattern?>(null) }
    var selectedPatterns by remember { mutableStateOf<List<Pattern>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPatternActions by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteMultipleDialog by remember { mutableStateOf(false) }

    val isSelectionMode = selectedPatterns.isNotEmpty()

    // Keeps list scroll position stable across recompositions.
    val listState = rememberLazyListState()

    val filteredPatterns = remember(allPatterns, searchQuery) {
        if (searchQuery.isEmpty()) {
            allPatterns
        } else {
            allPatterns.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Refresh patterns from storage. Default patterns are handled inside Pattern.loadAll.
    LaunchedEffect(Unit) {
        allPatterns = Pattern.loadAll(context)
        isLoading = false
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedPatterns = emptyList() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler")
                        }
                        Text(
                            text = "${selectedPatterns.size} sélectionné(s)",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge
                        )
                        // Delete all selected patterns
                        IconButton(onClick = { showDeleteMultipleDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour"
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Rechercher...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                singleLine = true
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer")
                                }
                            }
                        } else {
                            Text(
                                text = "Patterns",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            )
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Rechercher")
                            }
                        }
                    }
                }
            }
        },
        // Add button
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredPatterns.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aucun pattern disponible",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Créez ou importez des patterns en appuyant sur '+' en bas à droite de l'écran",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredPatterns) { pattern ->
                        PatternCard(
                            pattern = pattern,
                            isSelected = pattern in selectedPatterns,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                // add it to selected patterns if on selection mode
                                if (isSelectionMode) {
                                    selectedPatterns = if (pattern in selectedPatterns) {
                                        selectedPatterns - pattern
                                    } else {
                                        selectedPatterns + pattern
                                    }
                                // else open pattern actions
                                } else {
                                    selectedPattern = pattern
                                    showPatternActions = true
                                }
                            },
                            onLongClick = {
                                selectedPatterns = selectedPatterns + pattern
                            }
                        )
                    }
                }
            }

            if (selectedPattern != null && showPatternActions) {
                ModalBottomSheet(
                    onDismissRequest = {
                        selectedPattern = null
                        showPatternActions = false
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = selectedPattern!!.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        ListItem(
                            headlineContent = { Text("Jouer") },
                            leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            modifier = Modifier.clickable {
                                selectedPattern!!.play(context)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Modifier") },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onEditPattern(selectedPattern!!)
                                showPatternActions = false
                                selectedPattern = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Supprimer") },
                            leadingContent = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) },
                            modifier = Modifier.clickable { 
                                showPatternActions = false
                                showDeleteDialog = true 
                            }
                        )
                    }
                }
            }

            // Dialogs
            if (showDeleteDialog && selectedPattern != null) {
                DeletePatternDialog(
                    pattern = selectedPattern!!,
                    onConfirm = {
                        val updatedList = allPatterns.filter { it.name != selectedPattern!!.name }
                        Pattern.saveAll(context, updatedList)
                        allPatterns = updatedList
                        selectedPattern = null
                        showDeleteDialog = false
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }

            if (showCreateDialog) {
                CreatePatternDialog(
                    onCreate = { showCreateDialog = false; navigateTo(AppDestinations.STUDIO) },
                    onDismiss = { showCreateDialog = false }
                )
            }

            if (showDeleteMultipleDialog && selectedPatterns.isNotEmpty()) {
                DeleteMultiplePatternDialog(
                    patterns = selectedPatterns,
                    onConfirm = {
                        val updatedList = allPatterns.filter { it !in selectedPatterns }
                        Pattern.saveAll(context, updatedList)
                        allPatterns = updatedList
                        selectedPatterns = emptyList()
                        showDeleteMultipleDialog = false
                    },
                    onDismiss = {
                        showDeleteMultipleDialog = false
                    }
                )
            }
        }
    }
}

/**
 * Create a card representing a pattern
 */
@Composable
fun PatternCard(
    pattern: Pattern,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var playId by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pattern.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${pattern.timings.sum()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PatternBarsPreview(
                pattern = pattern,
                elapsedTime = elapsedTime,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            IconButton(onClick = {
                elapsedTime = 0L
                playId++
                val currentPlayId = playId
                val startTime = System.currentTimeMillis()

                pattern.play(context)

                scope.launch {
                    val totalDuration = pattern.timings.sum()

                    while(elapsedTime < totalDuration) {
                        if (playId != currentPlayId) return@launch
                        elapsedTime = System.currentTimeMillis() - startTime
                        delay(16L)
                    }

                    elapsedTime = 0L
                }
            }) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Lire",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Manage pattern creation dialog
 */
@Composable
fun CreatePatternDialog(
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un pattern") },
        text = {
            Text("Voulez-vous créer un nouveau pattern dans le studio ?")
        },
        confirmButton = {
            Button(onClick = onCreate) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

/**
 * Manage pattern deletion dialog
 */
@Composable
fun DeletePatternDialog(
    pattern: Pattern,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer le pattern") },
        text = { Text("Êtes-vous sûr de vouloir supprimer \"${pattern.name}\" ?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

/**
 * Manage multiple pattern deletion dialog
 */
@Composable
fun DeleteMultiplePatternDialog(
    patterns: List<Pattern>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer la sléection de patterns") },
        text = { Text("Êtes-vous sûr de vouloir supprimer ${patterns.size} patterns ?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

/**
 * Create a bar representation of a pattern
 */
@Composable
fun PatternBarsPreview(
    pattern: Pattern,
    elapsedTime: Long = 0L,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val msPerBar = 100L
    val maxAmplitude = 255f

    val bars = remember(pattern) {
        val result = mutableListOf<Float>()
        for (i in pattern.timings.indices) {
            val duration = pattern.timings[i]
            val amplitude = pattern.amplitudes[i]
            val numBars = (duration / msPerBar).coerceAtLeast(1)
            repeat(numBars.toInt()) {
                result.add(amplitude / maxAmplitude)
            }
        }
        result
    }

    val activeBarIndex = (elapsedTime / msPerBar).toInt()

    Row(
        modifier = modifier.height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { index, normalizedAmplitude ->
            val isActive = index <= activeBarIndex && elapsedTime > 0L
            val color = if (isActive)
                MaterialTheme.colorScheme.tertiary
            else
                barColor

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(normalizedAmplitude.coerceAtLeast(5F / maxAmplitude))
                    .background(
                        color = color,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}