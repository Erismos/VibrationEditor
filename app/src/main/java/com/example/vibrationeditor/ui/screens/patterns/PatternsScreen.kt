package com.example.vibrationeditor.ui.screens.patterns

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vibrationeditor.AppDestinations
import com.example.vibrationeditor.VibrationNotificationListener
import com.example.vibrationeditor.ui.screens.applications.NotificationType
import com.example.vibrationeditor.ui.screens.shared.AppListItem
import com.example.vibrationeditor.ui.screens.shared.AppRow
import com.example.vibrationeditor.ui.screens.shared.getInstalledApps
import com.example.vibrationeditor.ui.screens.shared.isNotificationServiceEnabled
import com.example.vibrationeditor.ui.screens.shared.AppMapping
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // State for the "Assign" workflow
    var showAppPicker by remember { mutableStateOf(false) }

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

    // Refresh patterns from storage.
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Text(
                            text = "${selectedPatterns.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = { showDeleteMultipleDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                                    contentDescription = "Back"
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Search...") },
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
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
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
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
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
                                text = "No patterns available",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Create or import patterns by tapping '+' in the bottom right",
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
                                if (isSelectionMode) {
                                    selectedPatterns = if (pattern in selectedPatterns) {
                                        selectedPatterns - pattern
                                    } else {
                                        selectedPatterns + pattern
                                    }
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
                            headlineContent = { Text("Play") },
                            leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            modifier = Modifier.clickable {
                                selectedPattern!!.play(context)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Edit") },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onEditPattern(selectedPattern!!)
                                showPatternActions = false
                                selectedPattern = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Assign") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showAppPicker = true
                                showPatternActions = false
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Delete") },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            modifier = Modifier.clickable { 
                                showPatternActions = false
                                showDeleteDialog = true 
                            }
                        )
                    }
                }
            }

            if (showAppPicker && selectedPattern != null) {
                AppPickerSheet(
                    pattern = selectedPattern!!,
                    onDismiss = { 
                        showAppPicker = false
                        selectedPattern = null 
                    },
                    onAssigned = { appName, channelName ->
                        showAppPicker = false
                        selectedPattern = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Pattern assigned to $appName ($channelName)")
                        }
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    pattern: Pattern,
    onDismiss: () -> Unit,
    onAssigned: (String, String) -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<AppListItem?>(null) }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isEmpty()) allApps else allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        allApps = getInstalledApps(context)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        BackHandler {
            if (selectedApp != null) selectedApp = null else onDismiss()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedApp == null) {
                Text(
                    "Assign to App",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(filteredApps) { app ->
                            AppRow(app, onClick = { selectedApp = app })
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                NotificationChannelPicker(
                    app = selectedApp!!,
                    pattern = pattern,
                    onBack = { selectedApp = null },
                    onAssigned = onAssigned
                )
            }
        }
    }
}

@Composable
fun NotificationChannelPicker(
    app: AppListItem,
    pattern: Pattern,
    onBack: () -> Unit,
    onAssigned: (String, String) -> Unit
) {
    val context = LocalContext.current
    val isPermissionGranted = isNotificationServiceEnabled(context)
    val listener = VibrationNotificationListener.instance

    val notificationTypes by produceState<List<NotificationType>>(initialValue = emptyList(), listener) {
        if (!isPermissionGranted || listener == null) {
            value = listOf(NotificationType("default", "All notifications"))
            return@produceState
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channels = listener.getNotificationChannels(app.packageName, android.os.Process.myUserHandle())
                value = if (channels.isNullOrEmpty()) {
                    listOf(NotificationType("default", "All notifications"))
                } else {
                    channels.map { NotificationType(it.id, it.name?.toString() ?: it.id) }
                }
            } catch (e: Exception) {
                value = listOf(NotificationType("default", "All notifications"))
            }
        } else {
            value = listOf(NotificationType("default", "All notifications"))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(app.name, style = MaterialTheme.typography.titleLarge)
        }

        if (!isPermissionGranted) {
            Button(
                onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                modifier = Modifier.padding(16.dp)
            ) { Text("Grant Notification Access") }
        }

        LazyColumn {
            items(notificationTypes) { type ->
                ListItem(
                    headlineContent = { Text(type.name) },
                    modifier = Modifier.clickable {
                        // Save the mapping
                        val allMappings = AppMapping.loadAll(context).toMutableMap()
                        val currentMapping = allMappings[app.packageName]?.channelMappings?.toMutableMap() ?: mutableMapOf()
                        currentMapping[type.id] = pattern.name
                        allMappings[app.packageName] = AppMapping(app.packageName, currentMapping)
                        AppMapping.saveAll(context, allMappings)
                        
                        onAssigned(app.name, type.name)
                    }
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
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
    var isPlaying by remember { mutableStateOf(false) }

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
                if (isPlaying) {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = context.getSystemService(VibratorManager::class.java)
                        manager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Vibrator::class.java)
                    }
                    vibrator?.cancel()
                    isPlaying = false
                    elapsedTime = 0L
                    playId++
                } else {
                    elapsedTime = 0L
                    isPlaying = true
                    playId++
                    val currentPlayId = playId
                    val startTime = System.currentTimeMillis()

                    pattern.play(context)

                    scope.launch {
                        val totalDuration = pattern.timings.sum()

                        while(elapsedTime < totalDuration) {
                            if (playId != currentPlayId) {
                                isPlaying = false
                                return@launch
                            }
                            elapsedTime = System.currentTimeMillis() - startTime
                            delay(16L)
                        }

                        elapsedTime = 0L
                        isPlaying = false
                    }
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
        title = { Text("Add pattern") },
        text = {
            Text("Do you want to create a new pattern in the Studio?")
        },
        confirmButton = {
            Button(onClick = onCreate) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Delete pattern") },
        text = { Text("Are you sure you want to delete \"${pattern.name}\"?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Delete selected patterns") },
        text = { Text("Are you sure you want to delete ${patterns.size} patterns?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
