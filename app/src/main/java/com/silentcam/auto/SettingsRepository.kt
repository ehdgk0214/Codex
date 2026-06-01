package com.silentcam.auto

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "silent_cam_settings")

class SettingsRepository(private val context: Context) {
    val autoApplyAfterBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_APPLY_AFTER_BOOT] ?: true
    }

    suspend fun setAutoApplyAfterBoot(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_APPLY_AFTER_BOOT] = enabled }
    }

    companion object {
        private val AUTO_APPLY_AFTER_BOOT = booleanPreferencesKey("auto_apply_after_boot")
    }
}
