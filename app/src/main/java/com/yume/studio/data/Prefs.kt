package com.yume.studio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val title: String) { DUSK("Сумерки"), SAKURA("Сакура"), SYSTEM("Как в системе") }

data class Settings(
    val theme: ThemeMode = ThemeMode.DUSK,
    val metronomeVolume: Float = 0.6f,
    val countInBars: Int = 1,
    val showNoteLabels: Boolean = true,
    val velocityByPosition: Boolean = true,
    val exportBits: Int = 24,
    val haptics: Boolean = true,
    val favorites: Set<String> = emptySet(),
    val sakuraPetals: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("yume_settings")

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val metroVol = floatPreferencesKey("metro_vol")
        val countIn = intPreferencesKey("count_in")
        val labels = booleanPreferencesKey("note_labels")
        val velPos = booleanPreferencesKey("vel_pos")
        val bits = intPreferencesKey("export_bits")
        val haptics = booleanPreferencesKey("haptics")
        val favs = stringSetPreferencesKey("favorites")
        val petals = booleanPreferencesKey("petals")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DUSK,
            metronomeVolume = p[K.metroVol] ?: 0.6f,
            countInBars = p[K.countIn] ?: 1,
            showNoteLabels = p[K.labels] ?: true,
            velocityByPosition = p[K.velPos] ?: true,
            exportBits = p[K.bits] ?: 24,
            haptics = p[K.haptics] ?: true,
            favorites = p[K.favs] ?: emptySet(),
            sakuraPetals = p[K.petals] ?: true,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(
                theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DUSK,
                metronomeVolume = p[K.metroVol] ?: 0.6f,
                countInBars = p[K.countIn] ?: 1,
                showNoteLabels = p[K.labels] ?: true,
                velocityByPosition = p[K.velPos] ?: true,
                exportBits = p[K.bits] ?: 24,
                haptics = p[K.haptics] ?: true,
                favorites = p[K.favs] ?: emptySet(),
                sakuraPetals = p[K.petals] ?: true,
            )
            val n = transform(cur)
            p[K.theme] = n.theme.name
            p[K.metroVol] = n.metronomeVolume
            p[K.countIn] = n.countInBars
            p[K.labels] = n.showNoteLabels
            p[K.velPos] = n.velocityByPosition
            p[K.bits] = n.exportBits
            p[K.haptics] = n.haptics
            p[K.favs] = n.favorites
            p[K.petals] = n.sakuraPetals
        }
    }

    suspend fun toggleFavorite(id: String) = update { s ->
        s.copy(favorites = if (id in s.favorites) s.favorites - id else s.favorites + id)
    }
}
