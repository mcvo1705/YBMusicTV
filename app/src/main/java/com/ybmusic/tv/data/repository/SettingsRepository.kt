package com.ybmusic.tv.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val KEY_QUALITY      = stringPreferencesKey("audio_quality")
    private val KEY_CACHE_URLS   = booleanPreferencesKey("cache_stream_urls")

    val audioQuality: Flow<String> = ctx.dataStore.data
        .map { it[KEY_QUALITY] ?: "high" }

    val cacheUrls: Flow<Boolean> = ctx.dataStore.data
        .map { it[KEY_CACHE_URLS] ?: true }

    suspend fun setAudioQuality(value: String) {
        ctx.dataStore.edit { it[KEY_QUALITY] = value }
    }

    suspend fun setCacheUrls(value: Boolean) {
        ctx.dataStore.edit { it[KEY_CACHE_URLS] = value }
    }
}
