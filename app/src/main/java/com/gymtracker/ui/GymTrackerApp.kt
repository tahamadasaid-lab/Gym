package com.gymtracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymtracker.ui.exercises.ExercisesScreen
import com.gymtracker.ui.history.HistoryScreen
import com.gymtracker.ui.history.SessionDetailScreen
import com.gymtracker.ui.progress.ProgressScreen
import com.gymtracker.ui.workout.WorkoutScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Workout("workout", "Séance", Icons.Filled.FitnessCenter),
    History("history", "Historique", Icons.Filled.History),
    Progress("progress", "Progrès", Icons.AutoMirrored.Filled.ShowChart),
    Exercises("exercises", "Exercices", Icons.AutoMirrored.Filled.FormatListBulleted),
}

@Composable
fun GymTrackerApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any {
                        it.route == tab.route || it.route?.startsWith(tab.route + "/") == true
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Workout.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.Workout.route) { WorkoutScreen() }
            composable(Tab.History.route) {
                HistoryScreen(onOpenSession = { id -> nav.navigate("history/$id") })
            }
            composable(
                route = "history/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                SessionDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(Tab.Progress.route) { ProgressScreen() }
            composable(Tab.Exercises.route) { ExercisesScreen() }
        }
    }
}
