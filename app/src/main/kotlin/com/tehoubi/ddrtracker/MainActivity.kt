package com.tehoubi.ddrtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
