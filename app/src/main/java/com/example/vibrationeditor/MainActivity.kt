package com.example.vibrationeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vibrationeditor.ui.theme.VibrationEditorTheme

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
                    icon = {
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.PATTERNS.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestinations.STUDIO.route) {
                Studio()
            }
            composable(AppDestinations.PATTERNS.route) {
                Patterns()
            }
            composable(AppDestinations.APPLICATIONS.route) {
                Applications()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
    val route: String
) {
    STUDIO("Studio", R.drawable.ic_home, "studio"),
    PATTERNS("Patterns", R.drawable.ic_favorite, "patterns"),
    APPLICATIONS("Applications", R.drawable.ic_account_box, "applications"),
}

@Composable
fun Studio() {
    Scaffold(
        topBar = { Text("Studio") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text("Studio Content")
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasQuadrantSize = size / 2F
                drawRect(
                    color = Color.Magenta,
                    size = canvasQuadrantSize
                )
            }
        }
    }
}

@Composable
fun Patterns() {
    Scaffold(
        topBar = { Text("Patterns") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text("Patterns Page")
        }
    }
}

@Composable
fun Applications() {
    Scaffold(
        topBar = { Text("Applications") }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text("Applications Page")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VibrationEditorTheme {
        Greeting("Android")
    }
}
