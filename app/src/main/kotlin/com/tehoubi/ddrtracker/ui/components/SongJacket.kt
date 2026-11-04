package com.tehoubi.ddrtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import com.tehoubi.ddrtracker.core.songs.SongData

/** The song list, for looking up a title's jacket. Empty (no jackets) unless provided. */
val LocalSongCatalog = compositionLocalOf { SongCatalog(emptyList()) }

/**
 * A song's jacket, downloaded the first time it's shown and cached on the
 * phone (see DdrTrackerApp), so the app ships none of them. A note stands
 * in while it loads, offline, or for a song the list doesn't know.
 */
@Composable
fun SongJacket(title: String, modifier: Modifier = Modifier, size: Dp = 48.dp, large: Boolean = false) {
    val catalog = LocalSongCatalog.current
    val id = remember(catalog, title) { catalog.find(title)?.id }
    Box(
        // A tint of the text colour shows on cards and plain backgrounds alike.
        modifier.size(size).clip(RoundedCornerShape(size / 8)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("♪", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (size.value * 0.4f).sp)
        if (id != null) {
            AsyncImage(
                model = SongData.jacketUrl(id, large),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
