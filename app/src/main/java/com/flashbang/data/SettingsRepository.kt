package com.flashbang.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Typed access to app settings (DataStore). Phase 1 keys only; Phase 3/5 add
 * N (ladder threshold), hint delay, and snooze defaults alongside these.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    object Keys {
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val OVERLAY_BLOCKING_OPTED_IN = booleanPreferencesKey("overlay_blocking_opted_in")
    }

    val vibrationEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.VIBRATION_ENABLED] ?: DEFAULT_VIBRATION_ENABLED }

    val overlayBlockingOptedIn: Flow<Boolean> =
        dataStore.data.map { it[Keys.OVERLAY_BLOCKING_OPTED_IN] ?: DEFAULT_OVERLAY_OPTED_IN }

    suspend fun vibrationEnabledNow(): Boolean = vibrationEnabled.first()

    suspend fun overlayBlockingOptedInNow(): Boolean = overlayBlockingOptedIn.first()

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setOverlayBlockingOptedIn(optedIn: Boolean) {
        dataStore.edit { it[Keys.OVERLAY_BLOCKING_OPTED_IN] = optedIn }
    }

    companion object {
        const val DEFAULT_VIBRATION_ENABLED = true
        const val DEFAULT_OVERLAY_OPTED_IN = false
    }
}
