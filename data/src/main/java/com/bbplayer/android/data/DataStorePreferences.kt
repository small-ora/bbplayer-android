package com.bbplayer.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bbPlayerDataStore: DataStore<Preferences> by preferencesDataStore(name = "bbplayer_settings")

/**
 * 用户设置仓库（androidx.datastore.preferences）。
 *
 * 默认值：themeMode=SYSTEM、dynamicColor=true、audioQuality=Q192、
 * autoplayOnStart=false、resumeOnStart=true、lyricsFontScale=1.0、lyricsOffsetMs=0。
 */
class DataStorePreferences(context: Context) : PreferencesRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.bbPlayerDataStore

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM
    }

    override val dynamicColor: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_COLOR] ?: true
    }

    override val audioQuality: Flow<AudioQuality> = dataStore.data.map { preferences ->
        AudioQuality.entries.firstOrNull { it.name == preferences[Keys.AUDIO_QUALITY] }
            ?: AudioQuality.DEFAULT
    }

    override val autoplayOnStart: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTOPLAY_ON_START] ?: false
    }

    override val resumeOnStart: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.RESUME_ON_START] ?: true
    }

    override val lyricsFontScale: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[Keys.LYRICS_FONT_SCALE] ?: DEFAULT_FONT_SCALE).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }

    override val lyricsOffsetMs: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[Keys.LYRICS_OFFSET_MS] ?: 0).coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
    }

    /** v1 暂不开放音量设置入口，仅保留默认值给播放器使用。 */
    override val lastNormalVolume: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.LAST_NORMAL_VOLUME] ?: 1f
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[Keys.AUDIO_QUALITY] = quality.name }
    }

    override suspend fun setAutoplayOnStart(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTOPLAY_ON_START] = enabled }
    }

    override suspend fun setResumeOnStart(enabled: Boolean) {
        dataStore.edit { it[Keys.RESUME_ON_START] = enabled }
    }

    override suspend fun setLyricsFontScale(scale: Float) {
        dataStore.edit { it[Keys.LYRICS_FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }
    }

    override suspend fun setLyricsOffsetMs(offsetMs: Int) {
        dataStore.edit { it[Keys.LYRICS_OFFSET_MS] = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS) }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val AUTOPLAY_ON_START = booleanPreferencesKey("autoplay_on_start")
        val RESUME_ON_START = booleanPreferencesKey("resume_on_start")
        val LYRICS_FONT_SCALE = floatPreferencesKey("lyrics_font_scale")
        val LYRICS_OFFSET_MS = intPreferencesKey("lyrics_offset_ms")
        val LAST_NORMAL_VOLUME = floatPreferencesKey("last_normal_volume")
    }

    private companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 2.0f
        const val MIN_OFFSET_MS = -5000
        const val MAX_OFFSET_MS = 5000
    }
}
