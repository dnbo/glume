package com.android.glume.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User settings and session. Secrets are never stored here (demo mode has none to store). */
@Immutable
data class Prefs(
    val loaded: Boolean = false,
    val serverUrl: String? = null,
    val userId: String? = null,
    // Slideshow
    val slideSource: String = "Favorites",
    val shuffle: Boolean = true,
    val intervalSec: Int = 6,
    val transition: String = "Crossfade",
    val kenBurns: Boolean = true,
    val captions: String = "Clock & place",
    val includeVideos: Boolean = false,
    // Screensaver
    val saver: Boolean = true,
    val saverAfter: String = "5 min",
    val saverSource: String = "Favorites",
    // Quality
    val photoQuality: String = "Preview (saves bandwidth)",
    val videoQuality: String = "Auto",
    // Accessibility
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
    val reduceMotion: Boolean = false,
    // Language
    val language: String = "English",
    val dateFormat: String = "21 Oct 2026",
    // Demo-only switches
    val simulateOffline: Boolean = false,
) {
    val signedIn get() = serverUrl != null
    val saverAfterMs: Long get() = SAVER_MS[saverAfter] ?: 300_000L

    companion object {
        val SAVER_MS = linkedMapOf("30 sec (demo)" to 30_000L, "2 min" to 120_000L, "5 min" to 300_000L, "10 min" to 600_000L, "30 min" to 1_800_000L)
    }
}

class SettingsStore(private val context: Context, scope: CoroutineScope) {

    private object K {
        val server = stringPreferencesKey("server_url")
        val user = stringPreferencesKey("user_id")
        val slideSource = stringPreferencesKey("slide_source")
        val shuffle = booleanPreferencesKey("shuffle")
        val interval = intPreferencesKey("interval")
        val transition = stringPreferencesKey("transition")
        val kenBurns = booleanPreferencesKey("ken_burns")
        val captions = stringPreferencesKey("captions")
        val videos = booleanPreferencesKey("include_videos")
        val saver = booleanPreferencesKey("saver")
        val saverAfter = stringPreferencesKey("saver_after")
        val saverSource = stringPreferencesKey("saver_source")
        val quality = stringPreferencesKey("photo_quality")
        val vquality = stringPreferencesKey("video_quality")
        val hc = booleanPreferencesKey("high_contrast")
        val large = booleanPreferencesKey("large_text")
        val reduce = booleanPreferencesKey("reduce_motion")
        val lang = stringPreferencesKey("language")
        val datefmt = stringPreferencesKey("date_format")
        val offline = booleanPreferencesKey("simulate_offline")
    }

    val prefs: StateFlow<Prefs> = context.dataStore.data.map { it.toPrefs() }
        .stateIn(scope, SharingStarted.Eagerly, Prefs())

    private fun Preferences.toPrefs(): Prefs {
        val d = Prefs()
        return Prefs(
            loaded = true,
            serverUrl = this[K.server],
            userId = this[K.user],
            slideSource = this[K.slideSource] ?: d.slideSource,
            shuffle = this[K.shuffle] ?: d.shuffle,
            intervalSec = this[K.interval] ?: d.intervalSec,
            transition = this[K.transition] ?: d.transition,
            kenBurns = this[K.kenBurns] ?: d.kenBurns,
            captions = this[K.captions] ?: d.captions,
            includeVideos = this[K.videos] ?: d.includeVideos,
            saver = this[K.saver] ?: d.saver,
            saverAfter = this[K.saverAfter] ?: d.saverAfter,
            saverSource = this[K.saverSource] ?: d.saverSource,
            photoQuality = this[K.quality] ?: d.photoQuality,
            videoQuality = this[K.vquality] ?: d.videoQuality,
            highContrast = this[K.hc] ?: d.highContrast,
            largeText = this[K.large] ?: d.largeText,
            reduceMotion = this[K.reduce] ?: d.reduceMotion,
            language = this[K.lang] ?: d.language,
            dateFormat = this[K.datefmt] ?: d.dateFormat,
            simulateOffline = this[K.offline] ?: d.simulateOffline,
        )
    }

    suspend fun signIn(serverUrl: String) = context.dataStore.edit { it[K.server] = serverUrl; it.remove(K.user) }
    suspend fun selectUser(id: String) = context.dataStore.edit { it[K.user] = id }
    suspend fun signOut() = context.dataStore.edit { it.remove(K.server); it.remove(K.user) }

    /** Generic setter used by Settings and the slideshow panel; [key] is a [SettingKey]. */
    suspend fun set(key: SettingKey, value: Any) = context.dataStore.edit {
        when (key) {
            SettingKey.SlideSource -> it[K.slideSource] = value as String
            SettingKey.Shuffle -> it[K.shuffle] = value as Boolean
            SettingKey.Interval -> it[K.interval] = value as Int
            SettingKey.Transition -> it[K.transition] = value as String
            SettingKey.KenBurns -> it[K.kenBurns] = value as Boolean
            SettingKey.Captions -> it[K.captions] = value as String
            SettingKey.IncludeVideos -> it[K.videos] = value as Boolean
            SettingKey.Saver -> it[K.saver] = value as Boolean
            SettingKey.SaverAfter -> it[K.saverAfter] = value as String
            SettingKey.SaverSource -> it[K.saverSource] = value as String
            SettingKey.PhotoQuality -> it[K.quality] = value as String
            SettingKey.VideoQuality -> it[K.vquality] = value as String
            SettingKey.HighContrast -> it[K.hc] = value as Boolean
            SettingKey.LargeText -> it[K.large] = value as Boolean
            SettingKey.ReduceMotion -> it[K.reduce] = value as Boolean
            SettingKey.Language -> it[K.lang] = value as String
            SettingKey.DateFormat -> it[K.datefmt] = value as String
            SettingKey.SimulateOffline -> it[K.offline] = value as Boolean
        }
    }
}

/** A user-editable setting: either a toggle or a cycle through [options]. */
enum class SettingKey(val options: List<Any> = emptyList(), val format: (Any) -> String = { it.toString() }) {
    SlideSource(listOf("Favorites", "All photos", "Memories", "Album: Lofoten 2023", "Person: Emma")),
    Shuffle,
    Interval(listOf(3, 6, 10, 20), { "$it seconds" }),
    Transition(listOf("Crossfade", "Fade to black", "None")),
    KenBurns,
    Captions(listOf("Clock & place", "Place only", "Off")),
    IncludeVideos,
    Saver,
    SaverAfter(Prefs.SAVER_MS.keys.toList()),
    SaverSource(listOf("Favorites", "Memories", "All photos", "Album: Lofoten 2023")),
    PhotoQuality(listOf("Preview (saves bandwidth)", "Original")),
    VideoQuality(listOf("Auto", "1080p", "Original")),
    HighContrast,
    LargeText,
    ReduceMotion,
    Language(listOf("English", "Svenska", "Deutsch", "Français")),
    DateFormat(listOf("21 Oct 2026", "2026-10-21", "Oct 21, 2026")),
    SimulateOffline;

    val isToggle get() = options.isEmpty()

    fun read(p: Prefs): Any = when (this) {
        SlideSource -> p.slideSource
        Shuffle -> p.shuffle
        Interval -> p.intervalSec
        Transition -> p.transition
        KenBurns -> p.kenBurns
        Captions -> p.captions
        IncludeVideos -> p.includeVideos
        Saver -> p.saver
        SaverAfter -> p.saverAfter
        SaverSource -> p.saverSource
        PhotoQuality -> p.photoQuality
        VideoQuality -> p.videoQuality
        HighContrast -> p.highContrast
        LargeText -> p.largeText
        ReduceMotion -> p.reduceMotion
        Language -> p.language
        DateFormat -> p.dateFormat
        SimulateOffline -> p.simulateOffline
    }

    /** Next value when the user presses OK / right (step = 1) or left (step = -1). */
    fun next(p: Prefs, step: Int): Any {
        val cur = read(p)
        if (isToggle) return !(cur as Boolean)
        val i = options.indexOf(cur).coerceAtLeast(0)
        return options[(i + step + options.size) % options.size]
    }

    fun display(p: Prefs): String = if (isToggle) (if (read(p) as Boolean) "On" else "Off") else format(read(p))
}
