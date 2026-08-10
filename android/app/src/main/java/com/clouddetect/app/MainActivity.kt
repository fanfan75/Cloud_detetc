package com.clouddetect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clouddetect.app.ui.CameraScreen
import com.clouddetect.app.ui.EncyclopediaScreen
import com.clouddetect.app.ui.ReverseSearchScreen

private sealed class Screen(val route: String, val label: String) {
    data object Camera : Screen("camera", "Reconnaître")
    data object Encyclopedia : Screen("encyclopedia", "Encyclopédie")
    data object Search : Screen("search", "Recherche")
}

private val screens = listOf(Screen.Camera, Screen.Encyclopedia, Screen.Search)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    CloudDetectApp()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun CloudDetectApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    val icon = when (screen) {
                        Screen.Camera -> Icons.Filled.CameraAlt
                        Screen.Encyclopedia -> Icons.Filled.MenuBook
                        Screen.Search -> Icons.Filled.Search
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Camera.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Camera.route) { CameraScreen() }
            composable(Screen.Encyclopedia.route) { EncyclopediaScreen() }
            composable(Screen.Search.route) { ReverseSearchScreen() }
        }
    }
}
