package com.tehoubi.ddrtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.scan.SongCatalogProvider
import com.tehoubi.ddrtracker.ui.components.LocalSongCatalog
import com.tehoubi.ddrtracker.ui.navigation.AppNavHost
import com.tehoubi.ddrtracker.ui.theme.DDRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var catalogs: SongCatalogProvider
    @Inject lateinit var bundled: SongCatalog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the system bars keep light icons even
        // when the phone itself is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val catalog by catalogs.catalog.collectAsStateWithLifecycle(bundled)
            CompositionLocalProvider(LocalSongCatalog provides catalog) {
                DDRTrackerTheme {
                    AppNavHost()
                }
            }
        }
    }
}
