package com.example.vibrationeditor

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vibrationeditor.ui.screens.applications.ApplicationsScreen
import com.example.vibrationeditor.ui.screens.patterns.PatternsScreen
import com.example.vibrationeditor.ui.screens.shared.AppMapping
import com.example.vibrationeditor.ui.screens.shared.Pattern
import com.example.vibrationeditor.ui.screens.studio.Studio
import com.example.vibrationeditor.ui.theme.VibrationEditorTheme

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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnNotNull = sbn ?: return
        val packageName = sbnNotNull.packageName
        
        // On ignore les notifications de notre propre application pour éviter les boucles
        if (packageName == this.packageName) return

        // Récupération du canal (API 26+)
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbnNotNull.notification.channelId ?: "default"
        } else {
            "default"
        }

        Log.d("VibrationEditor", "Notification reçue: $packageName (canal: $channelId)")

        // 1. Charger les mappings sauvegardés
        val allMappings = AppMapping.loadAll(this)
        val appMapping = allMappings[packageName] ?: return

        // 2. Trouver le pattern associé (canal spécifique ou global pour l'app)
        val patternName = appMapping.channelMappings[channelId] ?: appMapping.channelMappings["default"] ?: return

        // 3. Charger le pattern et le jouer
        val allPatterns = Pattern.loadAll(this)
        val pattern = allPatterns.find { it.name == patternName } ?: return

        Log.d("VibrationEditor", "Déclenchement du pattern: $patternName")
        pattern.play(this)
    }

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

    // Global state for unsaved changes in Studio
    var isStudioDirty by remember { mutableStateOf(false) }
    var pendingNavigationDestination by remember { mutableStateOf<AppDestinations?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    
    // Pattern currently being edited in the Studio
    var patternToEdit by remember { mutableStateOf<Pattern?>(null) }

    val navigateTo = { destination: AppDestinations ->
        if (isStudioDirty && currentDestination?.route == AppDestinations.STUDIO.route) {
            pendingNavigationDestination = destination
            showUnsavedDialog = true
        } else {
            // Reset patternToEdit if we are going to Studio directly from the menu
            // but NOT if we are being redirected by an edit action (handled in navigateToEdit)
            if (destination != AppDestinations.STUDIO) {
                patternToEdit = null
            }
            
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = { Icon(painterResource(destination.icon), contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { navigateTo(destination) }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.PATTERNS.route, modifier = Modifier.fillMaxSize()) {
            composable(AppDestinations.STUDIO.route) { 
                Studio(
                    patternToEdit = patternToEdit,
                    onDirtyStateChanged = { isStudioDirty = it },
                    onDismissDialog = { showUnsavedDialog = true }
                ) 
            }
            composable(AppDestinations.PATTERNS.route) {
                PatternsScreen(
                    navigateTo = navigateTo,
                    onEditPattern = { pattern ->
                        patternToEdit = pattern
                        navController.navigate(AppDestinations.STUDIO.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(AppDestinations.APPLICATIONS.route) { ApplicationsScreen() }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Changements non sauvegardés") },
                text = { Text("Vous avez des modifications non sauvegardées dans le Studio. Voulez-vous les ignorer et quitter ?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedDialog = false
                            isStudioDirty = false // Autorise la navigation
                            pendingNavigationDestination?.let { dest ->
                                patternToEdit = null // Reset editing state
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Ignorer et quitter") }
                },
                dismissButton = {
                    TextButton(onClick = { showUnsavedDialog = false }) { Text("Rester") }
                }
            )
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

/**
 * Triggers a simple one-shot vibration.
 */
fun triggerVibration(context: Context, duration: Long = 500) {
    Pattern("Test", longArrayOf(duration), intArrayOf(255)).play(context)
}

/** Compose preview entry point. */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VibrationEditorTheme { VibrationEditorApp() }
}
