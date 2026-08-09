package com.example.accessibilitymacro.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.accessibilitymacro.model.MacroPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.macroDataStore by preferencesDataStore(name = "macro_presets")

class MacroPresetRepository(private val context: Context) {

    private val presetsKey = stringPreferencesKey("presets_json")
    private val json = Json { ignoreUnknownKeys = true }

    val presetsFlow: Flow<List<MacroPreset>> = context.macroDataStore.data.map { prefs ->
        val raw = prefs[presetsKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<MacroPreset>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun savePreset(preset: MacroPreset) {
        val current = readCurrent()
        val updated = current.filterNot { it.id == preset.id } + preset
        writeAll(updated)
    }

    suspend fun deletePreset(id: String) {
        val current = readCurrent()
        writeAll(current.filterNot { it.id == id })
    }

    private suspend fun readCurrent(): List<MacroPreset> {
        val prefs = context.macroDataStore.data
        var result: List<MacroPreset> = emptyList()
        prefs.map { p ->
            val raw = p[presetsKey] ?: return@map emptyList<MacroPreset>()
            runCatching { json.decodeFromString<List<MacroPreset>>(raw) }.getOrDefault(emptyList())
        }.collectFirstInto { result = it }
        return result
    }

    private suspend fun writeAll(list: List<MacroPreset>) {
        context.macroDataStore.edit { prefs ->
            prefs[presetsKey] = json.encodeToString(list)
        }
    }
}

private suspend fun <T> Flow<T>.collectFirstInto(into: (T) -> Unit) {
    kotlinx.coroutines.flow.first(this).let(into)
}
