package com.example.vibrationeditor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vibrationeditor.ui.screens.patterns.PatternsScreen
import com.example.vibrationeditor.ui.theme.VibrationEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notification listener service used to detect when the app is connected as a
 * notification listener.
 *
 * The [instance] reference is exposed as Compose state so UI can react when the
 * service connects or disconnects.
 */
class VibrationNotificationListener : NotificationListenerService() {
    companion object {
        /** Current connected service instance, or null when disconnected. */
        var instance by mutableStateOf<VibrationNotificationListener?>(null)
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("VibrationEditor", "Service connecté")
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("VibrationEditor", "Service déconnecté")
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}

/**
 * Entry activity hosting the Compose application.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibrationEditorTheme {
                VibrationEditorApp()
            }
        }
    }
}

/**
 * Root composable containing the adaptive navigation scaffold.
 */
@PreviewScreenSizes
@Composable
fun VibrationEditorApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = { Icon(painterResource(destination.icon), contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.PATTERNS.route, modifier = Modifier.fillMaxSize()) {
            composable(AppDestinations.STUDIO.route) { Studio() }
            composable(AppDestinations.PATTERNS.route) { PatternsScreen() }
            composable(AppDestinations.APPLICATIONS.route) { Applications() }
        }
    }
}

/**
 * Top-level destinations displayed in the navigation suite.
 */
enum class AppDestinations(val label: String, val icon: Int, val route: String) {
    STUDIO("Studio", R.drawable.ic_home, "studio"),
    PATTERNS("Patterns", R.drawable.ic_favorite, "patterns"),
    APPLICATIONS("Applications", R.drawable.ic_account_box, "applications"),
}

/**
 * App top bar used by each primary screen.
 *
 * @param title Screen title.
 */
@Composable
fun StableTopAppBar(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Text(title, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), style = MaterialTheme.typography.titleLarge)
    }
}

/** Studio screen placeholder. */
@Composable
fun Studio() {
    Scaffold(topBar = { StableTopAppBar("Studio") }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text("Studio Content")
        }
    }
}

/** Patterns screen placeholder. */
/** In screens.patterns.Patterns */

/**
 * Triggers a one-shot vibration using the appropriate API for the device SDK.
 *
 * @param context Context used to get the vibrator service.
 * @param duration Vibration duration in milliseconds.
 */
fun triggerVibration(context: Context, duration: Long = 500) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, audioAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}

/** Presentation model for an installed launchable application. */
data class AppListItem(val name: String, val packageName: String, val icon: Drawable)

/** Presentation model for a notification channel/category. */
data class NotificationType(val id: String, val name: String, var patternName: String = "Par défaut")

/** Presentation model for a vibration pattern choice. */
data class VibrationPattern(val name: String)

/**
 * Applications screen listing launchable apps and providing a details view.
 */
@Composable
fun Applications() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppListItem?>(null) }

    // Keeps list scroll position stable across recompositions.
    val listState = rememberLazyListState()

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isEmpty()) allApps else allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        allApps = getInstalledApps(context)
        isLoading = false
    }

    if (selectedApp != null) {
        // Closes the details pane first when Android back is pressed.
        BackHandler { selectedApp = null }
        
        AppDetailView(app = selectedApp!!, onBack = { selectedApp = null })
    } else {
        Scaffold(
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isSearchActive) {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                            TextField(
                                value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f),
                                placeholder = { Text("Rechercher...") },
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                singleLine = true
                            )
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Effacer") }
                        } else {
                            Text(text = "Applications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp).weight(1f))
                            IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, contentDescription = "Rechercher") }
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(state = listState, modifier = Modifier.padding(innerPadding)) {
                    items(filteredApps) { app ->
                        AppRow(app, onClick = { selectedApp = app })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Details screen for one application with per-notification-type pattern selection.
 *
 * @param app Selected app.
 * @param onBack Called when user navigates back.
 */
@Composable
fun AppDetailView(app: AppListItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isRestricted by remember { mutableStateOf(false) }

    // Gestion de la sélection du pattern
    var showSheet by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<NotificationType?>(null) }
    val patternOverrides = remember { mutableStateMapOf<String, String>() }

    // Temporary patterns until Studio-defined patterns are wired in.
    val availablePatterns = remember {
        listOf(
            VibrationPattern("S.O.S"),
            VibrationPattern("Battement de cœur"),
            VibrationPattern("Rapide"),
            VibrationPattern("Long")
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listener = VibrationNotificationListener.instance
    val notificationTypes by produceState<List<NotificationType>>(initialValue = emptyList(), isPermissionGranted, listener, app.packageName) {
        if (!isPermissionGranted || listener == null) {
            value = listOf(NotificationType("default", "Toutes les notifications"))
            return@produceState
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Attempts to query app-specific channels (may be restricted).
                val channels = listener.getNotificationChannels(app.packageName, Process.myUserHandle())
                Log.d("VibrationEditor", "Canaux pour ${app.packageName} : ${channels?.size ?: "null"}")
                
                value = if (channels.isNullOrEmpty()) {
                    listOf(NotificationType("default", "Toutes les notifications"))
                } else {
                    channels.map { NotificationType(it.id, it.name?.toString() ?: it.id) }
                }
                isRestricted = false
            } catch (e: SecurityException) {
                // Android often blocks this for third-party apps.
                Log.w("VibrationEditor", "L'accès aux canaux de ${app.packageName} est restreint par Android.")
                isRestricted = true
                value = listOf(NotificationType("default", "Toutes les notifications"))
            } catch (e: Exception) {
                Log.e("VibrationEditor", "Erreur lors de la récupération des canaux", e)
                value = listOf(NotificationType("default", "Toutes les notifications"))
            }
        } else {
            value = listOf(NotificationType("default", "Toutes les notifications"))
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    Image(bitmap = app.icon.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(text = app.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            if (!isPermissionGranted) {
                PermissionWarning { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
            } else if (listener == null) {
                // Service is granted but not connected yet.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Le service d'écoute est en cours de démarrage...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (isRestricted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Android restreint l'accès aux catégories individuelles des autres applications. Les réglages s'appliqueront à toutes les notifications de cette application.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text("Types de notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(notificationTypes) { type ->
                    val currentPattern = patternOverrides[type.id] ?: "Par défaut"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.name, style = MaterialTheme.typography.bodyLarge)
                            Text(currentPattern, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { 
                            editingType = type
                            showSheet = true 
                        }) { 
                            Icon(Icons.Default.Settings, contentDescription = "Modifier", tint = MaterialTheme.colorScheme.outline) 
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Choisir un pattern",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    LazyColumn {
                        // Default behavior option.
                        item {
                            PatternOptionRow(
                                name = "Par défaut",
                                isSelected = (patternOverrides[editingType?.id] ?: "Par défaut") == "Par défaut",
                                onPlay = { triggerVibration(context, 200) },
                                onClick = {
                                    editingType?.let { patternOverrides.remove(it.id) }
                                    showSheet = false
                                }
                            )
                        }
                        
                        // User-defined patterns.
                        items(availablePatterns) { pattern ->
                            PatternOptionRow(
                                name = pattern.name,
                                isSelected = patternOverrides[editingType?.id] == pattern.name,
                                onPlay = { triggerVibration(context, 500) },
                                onClick = {
                                    editingType?.let { patternOverrides[it.id] = pattern.name }
                                    showSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Row used by the bottom sheet to pick and test one vibration pattern.
 *
 * @param name Display name.
 * @param isSelected Whether this pattern is currently selected.
 * @param onPlay Callback to test the pattern.
 * @param onClick Callback to select the pattern.
 */
@Composable
fun PatternOptionRow(name: String, isSelected: Boolean, onPlay: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Tester",
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Warning card prompting the user to grant notification listener access.
 *
 * @param onSettingsClick Opens the relevant system settings.
 */
@Composable
fun PermissionWarning(onSettingsClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("L'accès aux notifications est requis pour personnaliser les vibrations.")
            Button(onClick = onSettingsClick, modifier = Modifier.padding(top = 8.dp)) { Text("Donner l'accès") }
        }
    }
}

/**
 * One row in the applications list.
 *
 * @param app App displayed by the row.
 * @param onClick Called when the row is tapped.
 */
@Composable
fun AppRow(app: AppListItem, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(bitmap = app.icon.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/**
 * Returns whether this app is currently enabled as a notification listener.
 *
 * @param context Context used to read secure settings.
 */
fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && TextUtils.equals(pkgName, cn.packageName)) return true
        }
    }
    return false
}

/**
 * Loads installed launchable apps on a background dispatcher.
 *
 * @param context Context used to query [PackageManager].
 * @return Alphabetically sorted list of launchable applications.
 */
suspend fun getInstalledApps(context: Context): List<AppListItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { AppListItem(name = it.loadLabel(pm).toString(), packageName = it.packageName, icon = it.loadIcon(pm)) }
        .sortedBy { it.name.lowercase() }
}

    /** Compose preview entry point. */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VibrationEditorTheme { VibrationEditorApp() }
}
