package com.example.vibrationeditor.ui.screens.patterns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.vibrationeditor.AppDestinations
import com.example.vibrationeditor.ui.screens.shared.Pattern

// Test patterns
val testPatterns = listOf(
    Pattern("Court", longArrayOf(0, 100), intArrayOf(0, 255)),
    Pattern("Double", longArrayOf(0, 100, 100, 100), intArrayOf(0, 255, 0, 255)),
    Pattern("Alternative", longArrayOf(0, 500, 50, 500, 500, 500), intArrayOf(0, 255, 0, 10, 0, 255))
)

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Patterns screen listing available patterns and details.
 */
@Composable
fun PatternsScreen(navigateTo: (AppDestinations) -> Unit) {
    val context = LocalContext.current
    var allPatterns by remember { mutableStateOf<List<Pattern>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var selectedPattern by remember { mutableStateOf<Pattern?>(null) }
    var showPatternActions by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Keeps list scroll position stable across recompositions.
    val listState = rememberLazyListState()

    val filteredPatterns = remember(allPatterns, searchQuery) {
        if (searchQuery.isEmpty()) {
            allPatterns
        } else {
            allPatterns.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(Unit) {
        allPatterns = Pattern.loadAll(context).ifEmpty { testPatterns }
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
        },
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
        } else  {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredPatterns.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun pattern disponible\n" +
                                        "\n" +
                                        "Créez ou importez des patterns en appuyant sur '+' en bas à droite de l'écran",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredPatterns) { pattern ->
                        PatternCard(pattern = pattern) {
                            selectedPattern = pattern
                            showPatternActions = true
                        }
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
                    ) {
                        Text(
                            text = selectedPattern!!.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        BottomSheetItem("Jouer", { selectedPattern!!.play(context) })
                        BottomSheetItem("Assigner à une Application", { /* assign */ })
                        BottomSheetItem("Modifier dans le Studio", { /* assign */ })
                        BottomSheetItem("Supprimer", { showCreateDialog = false; showDeleteDialog = true }, Color.Red)
                    }
                }
            }

            if (showDeleteDialog) {
                DeletePatternDialog(
                    pattern = selectedPattern,
                    onConfirm = {
                        // delete pattern
                        allPatterns = allPatterns.filter { it != selectedPattern }

                        selectedPattern = null
                        showDeleteDialog = false
                        showPatternActions = false
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }

            if (showCreateDialog) {
                CreatePatternDialog(
                    onCreate = { showCreateDialog = false; navigateTo(AppDestinations.STUDIO) },
                    onImport = { /* import */},
                    onDismiss = { showCreateDialog = false }
                )
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
fun PatternCard(pattern: Pattern, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .clickable(onClick = onClick)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pattern.name,
                fontSize = 16.sp
            )

            Button(onClick = { pattern.play(context) }) {
                Icon(
                    Icons.Default.PlayArrow,
                    "Lire"
                )
            }
        }
    }
}

/**
 * Create an element of the sheet for pattern actions
 *
 * @param text Action name.
 * @param onClick Action itself.
 * @param color Text color.
 */
@Composable
fun BottomSheetItem(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = color)
    }
}

/**
 * Manage pattern creation dialog
 *
 * @param onCreate Create a pattern in the studio.
 * @param onImport Import a pattern.
 * @param onDismiss Close dialog.
 */
@Composable
fun CreatePatternDialog(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un pattern") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Créer dans le studio")
                }

                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importer un fichier")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annuler")
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun DeletePatternDialog(
    pattern: Pattern?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("Supprimer le pattern") },
        text = { Text("Êtes-vous sûr de vouloir supprimer \"${pattern!!.name}\" ?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            // cancel
            OutlinedButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}