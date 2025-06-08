package com.tehoubi.ddrtracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    /** Take the still automatically once the live reading settles. */
    val autoCapture: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.dataStore

    private object Keys {
        val autoCapture = booleanPreferencesKey("auto_capture")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(autoCapture = p[Keys.autoCapture] ?: true)
    }

    suspend fun setAutoCapture(enabled: Boolean) = store.edit { it[Keys.autoCapture] = enabled }
}
