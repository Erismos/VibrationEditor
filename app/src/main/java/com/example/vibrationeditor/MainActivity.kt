package com.example.vibrationeditor

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
            composable(AppDestinations.APPLICATIONS.route) { ApplicationsScreen() }
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

/** Compose preview entry point. */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VibrationEditorTheme { VibrationEditorApp() }
}
