package com.bbplayer.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbplayer.android.core.contracts.PreferencesRepository
import com.bbplayer.android.core.model.AccentPreset
import com.bbplayer.android.core.model.AudioQuality
import com.bbplayer.android.core.model.LyricSource
import com.bbplayer.android.core.model.LyricVisualStyle
import com.bbplayer.android.core.model.MiniPlayerStyle
import com.bbplayer.android.core.model.PlayerBackgroundStyle
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

    // ---------------- v2 appearance / lyrics / downloads ----------------

    override val playerBackgroundStyle: Flow<PlayerBackgroundStyle> = dataStore.data.map { preferences ->
        PlayerBackgroundStyle.entries.firstOrNull { it.name == preferences[Keys.PLAYER_BACKGROUND_STYLE] }
            ?: PlayerBackgroundStyle.GRADIENT
    }

    override val miniPlayerStyle: Flow<MiniPlayerStyle> = dataStore.data.map { preferences ->
        MiniPlayerStyle.entries.firstOrNull { it.name == preferences[Keys.MINI_PLAYER_STYLE] }
            ?: MiniPlayerStyle.DOCK
    }

    override val lyricVisualStyle: Flow<LyricVisualStyle> = dataStore.data.map { preferences ->
        LyricVisualStyle.entries.firstOrNull { it.name == preferences[Keys.LYRIC_VISUAL_STYLE] }
            ?: LyricVisualStyle.MODERN
    }

    override val verbatimLyrics: Flow<Boolean> = dataStore.data.map { it[Keys.VERBATIM_LYRICS] ?: true }

    override val accentPreset: Flow<AccentPreset> = dataStore.data.map { preferences ->
        AccentPreset.entries.firstOrNull { it.name == preferences[Keys.ACCENT_PRESET] } ?: AccentPreset.SALT
    }

    override val showTranslation: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_TRANSLATION] ?: true }

    override val statusBarLyricLine: Flow<Boolean> = dataStore.data.map { it[Keys.STATUS_BAR_LYRIC_LINE] ?: true }

    override val desktopLyricsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DESKTOP_LYRICS_ENABLED] ?: false }

    override val desktopLyricsTextSize: Flow<Float> = dataStore.data.map {
        (it[Keys.DESKTOP_LYRICS_TEXT_SIZE] ?: 18f).coerceIn(10f, 40f)
    }

    override val desktopLyricsColor: Flow<Long> = dataStore.data.map { it[Keys.DESKTOP_LYRICS_COLOR] ?: 0xFFFFC107L }

    override val downloadParallel: Flow<Int> = dataStore.data.map {
        (it[Keys.DOWNLOAD_PARALLEL] ?: 1).coerceIn(1, 3)
    }

    override val autoCheckUpdate: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_CHECK_UPDATE] ?: true }

    override val lyricSource: Flow<LyricSource> = dataStore.data.map { preferences ->
        LyricSource.entries.firstOrNull { it.name == preferences[Keys.LYRIC_SOURCE] } ?: LyricSource.AUTO
    }

    override val importBatchSize: Flow<Int> = dataStore.data.map {
        (it[Keys.IMPORT_BATCH_SIZE] ?: 50).coerceIn(10, 200)
    }

    override suspend fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        dataStore.edit { it[Keys.PLAYER_BACKGROUND_STYLE] = style.name }
    }

    override suspend fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        dataStore.edit { it[Keys.MINI_PLAYER_STYLE] = style.name }
    }

    override suspend fun setLyricVisualStyle(style: LyricVisualStyle) {
        dataStore.edit { it[Keys.LYRIC_VISUAL_STYLE] = style.name }
    }

    override suspend fun setVerbatimLyrics(enabled: Boolean) {
        dataStore.edit { it[Keys.VERBATIM_LYRICS] = enabled }
    }

    override suspend fun setAccentPreset(preset: AccentPreset) {
        dataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    override suspend fun setShowTranslation(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_TRANSLATION] = enabled }
    }

    override suspend fun setStatusBarLyricLine(enabled: Boolean) {
        dataStore.edit { it[Keys.STATUS_BAR_LYRIC_LINE] = enabled }
    }

    override suspend fun setDesktopLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DESKTOP_LYRICS_ENABLED] = enabled }
    }

    override suspend fun setDesktopLyricsTextSize(size: Float) {
        dataStore.edit { it[Keys.DESKTOP_LYRICS_TEXT_SIZE] = size.coerceIn(10f, 40f) }
    }

    override suspend fun setDesktopLyricsColor(color: Long) {
        dataStore.edit { it[Keys.DESKTOP_LYRICS_COLOR] = color }
    }

    override suspend fun setDownloadParallel(count: Int) {
        dataStore.edit { it[Keys.DOWNLOAD_PARALLEL] = count.coerceIn(1, 3) }
    }

    override suspend fun setAutoCheckUpdate(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CHECK_UPDATE] = enabled }
    }

    override suspend fun setLyricSource(source: LyricSource) {
        dataStore.edit { it[Keys.LYRIC_SOURCE] = source.name }
    }

    override suspend fun setImportBatchSize(size: Int) {
        dataStore.edit { it[Keys.IMPORT_BATCH_SIZE] = size.coerceIn(10, 200) }
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
        val PLAYER_BACKGROUND_STYLE = stringPreferencesKey("player_background_style")
        val MINI_PLAYER_STYLE = stringPreferencesKey("mini_player_style")
        val LYRIC_VISUAL_STYLE = stringPreferencesKey("lyric_visual_style")
        val VERBATIM_LYRICS = booleanPreferencesKey("verbatim_lyrics")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val SHOW_TRANSLATION = booleanPreferencesKey("show_translation")
        val STATUS_BAR_LYRIC_LINE = booleanPreferencesKey("status_bar_lyric_line")
        val DESKTOP_LYRICS_ENABLED = booleanPreferencesKey("desktop_lyrics_enabled")
        val DESKTOP_LYRICS_TEXT_SIZE = floatPreferencesKey("desktop_lyrics_text_size")
        val DESKTOP_LYRICS_COLOR = longPreferencesKey("desktop_lyrics_color")
        val DOWNLOAD_PARALLEL = intPreferencesKey("download_parallel")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val LYRIC_SOURCE = stringPreferencesKey("lyric_source")
        val IMPORT_BATCH_SIZE = intPreferencesKey("import_batch_size")
    }

    private companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 2.0f
        const val MIN_OFFSET_MS = -5000
        const val MAX_OFFSET_MS = 5000
    }
}
