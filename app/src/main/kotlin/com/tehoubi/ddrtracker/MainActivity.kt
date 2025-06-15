package com.tehoubi.ddrtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tehoubi.ddrtracker.ui.screens.HomeScreen
import com.tehoubi.ddrtracker.ui.review.ReviewScreen
import com.tehoubi.ddrtracker.ui.scan.ScanScreen
import com.tehoubi.ddrtracker.ui.theme.DDRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DDRTrackerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onScanClick = { navController.navigate("scan") },
                            onPlayClick = { },
                        )
                    }
                    composable("scan") {
                        ScanScreen(
                            onBack = { navController.popBackStack() },
                            onReview = { navController.navigate("review/SCAN") },
                        )
                    }
                    composable(
                        "review/{mode}?playId={playId}",
                        arguments = listOf(
                            navArgument("mode") { type = NavType.StringType },
                            navArgument("playId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        ),
                    ) {
                        ReviewScreen(
                            onClose = { navController.popBackStack() },
                            onSaved = { navController.popBackStack("home", inclusive = false) },
                        )
                    }
                }
            }
        }
    }
}
