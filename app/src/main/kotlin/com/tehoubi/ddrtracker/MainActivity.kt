package com.tehoubi.ddrtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tehoubi.ddrtracker.ui.screens.HomeScreen
import com.tehoubi.ddrtracker.ui.screens.ScanScreen
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
                        ScanScreen(onScoreConfirmed = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
