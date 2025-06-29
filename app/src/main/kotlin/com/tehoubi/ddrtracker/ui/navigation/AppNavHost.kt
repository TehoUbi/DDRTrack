package com.tehoubi.ddrtracker.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tehoubi.ddrtracker.ui.chart.ChartScreen
import com.tehoubi.ddrtracker.ui.history.HistoryScreen
import com.tehoubi.ddrtracker.ui.home.HomeScreen
import com.tehoubi.ddrtracker.ui.play.PlayDetailScreen
import com.tehoubi.ddrtracker.ui.progress.ProgressScreen
import com.tehoubi.ddrtracker.ui.review.ReviewMode
import com.tehoubi.ddrtracker.ui.review.ReviewScreen
import com.tehoubi.ddrtracker.ui.scan.ScanScreen
import com.tehoubi.ddrtracker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val SCAN = "scan"
    const val SETTINGS = "settings"
    const val REVIEW = "review/{mode}?playId={playId}"
    const val PLAY = "play/{playId}"
    const val CHART = "chart/{songKey}/{style}/{difficulty}"

    fun review(mode: ReviewMode, playId: String? = null) =
        "review/${mode.name}" + (playId?.let { "?playId=$it" } ?: "")

    fun play(id: String) = "play/$id"
    fun chart(songKey: String, style: String, difficulty: String) = "chart/$songKey/$style/$difficulty"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.HISTORY, "History", Icons.AutoMirrored.Filled.List),
    Tab(Routes.PROGRESS, "Progress", Icons.Default.DateRange),
)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { outer ->
        val inner = PaddingValues(bottom = outer.calculateBottomPadding())
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScanClick = { navController.navigate(Routes.SCAN) },
                    onManualClick = { navController.navigate(Routes.review(ReviewMode.MANUAL)) },
                    onPlayClick = { navController.navigate(Routes.play(it)) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    contentPadding = inner,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onPlayClick = { navController.navigate(Routes.play(it)) }, contentPadding = inner)
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(contentPadding = inner)
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                    onReview = {
                        navController.navigate(Routes.review(ReviewMode.SCAN)) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.REVIEW,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("playId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                ReviewScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { id ->
                        val editing = it.arguments?.getString("mode") == ReviewMode.EDIT.name
                        if (editing) navController.popBackStack()
                        else navController.navigate(Routes.play(id)) { popUpTo(Routes.HOME) }
                    },
                )
            }
            composable(Routes.PLAY, arguments = listOf(navArgument("playId") { type = NavType.StringType })) {
                PlayDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.review(ReviewMode.EDIT, id)) },
                    onChart = { p -> navController.navigate(Routes.chart(p.songKey, p.style.name, p.difficulty.name)) },
                )
            }
            composable(Routes.CHART) {
                ChartScreen(
                    onBack = { navController.popBackStack() },
                    onPlayClick = { navController.navigate(Routes.play(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
