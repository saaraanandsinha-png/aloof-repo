package com.example.saaraapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.saaraapp.ui.screens.CalendarScreen
import com.example.saaraapp.ui.screens.ChatScreen
import com.example.saaraapp.ui.screens.SettingsScreen
import com.example.saaraapp.ui.screens.TodayScreen
import com.example.saaraapp.ui.screens.WeekScreen
import kotlinx.serialization.Serializable

@Serializable sealed class Screen {
    @Serializable data object Today    : Screen()
    @Serializable data object Week     : Screen()
    @Serializable data object Calendar : Screen()
    @Serializable data object Chat     : Screen()
    @Serializable data object Settings : Screen()
}

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Today,
        modifier         = modifier
    ) {
        composable<Screen.Today>    { TodayScreen() }
        composable<Screen.Week>     { WeekScreen() }
        composable<Screen.Calendar> { CalendarScreen() }
        composable<Screen.Chat>     { ChatScreen() }
        composable<Screen.Settings> { SettingsScreen(navController) }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current   = backStack?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Today, "Today") },
            label    = { Text("Today") },
            selected = current?.contains("Today") == true,
            onClick  = { navController.navigate(Screen.Today) { launchSingleTop = true } }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.DateRange, "Week") },
            label    = { Text("Week") },
            selected = current?.contains("Week") == true,
            onClick  = { navController.navigate(Screen.Week) { launchSingleTop = true } }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.CalendarMonth, "Calendar") },
            label    = { Text("Calendar") },
            selected = current?.contains("Calendar") == true,
            onClick  = { navController.navigate(Screen.Calendar) { launchSingleTop = true } }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Chat, "Chat") },
            label    = { Text("Chat") },
            selected = current?.contains("Chat") == true,
            onClick  = { navController.navigate(Screen.Chat) { launchSingleTop = true } }
        )
    }
}
