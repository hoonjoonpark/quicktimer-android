package com.quicktimer.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.nio.charset.StandardCharsets

private val Context.legacyPresetDataStore by preferencesDataStore(name = "timer_presets")

class TimerPresetStore(
    private val context: Context,
    private val dao: TimerPresetDao
) {
    private val migrationPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val legacyPresetsKey = stringPreferencesKey("presets_v1")
    private val legacyInitializedKey = booleanPreferencesKey("presets_initialized_v1")
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrationJob: Job = storeScope.launch {
        migrateLegacyIfNeeded()
    }

    val presetsFlow: Flow<List<TimerPreset>> = flow {
        awaitMigration()
        emitAll(
            dao.observeAll().map { list ->
                list.map { it.toModel() }
            }
        )
    }

    suspend fun ensureDefaultsIfNeeded() {
        awaitMigration()
        seedDefaultsIfNeeded()
    }

    private suspend fun awaitMigration() {
        migrationJob.join()
    }

    suspend fun addPreset(durationSeconds: Int, label: String) {
        if (durationSeconds <= 0) return
        awaitMigration()
        val current = dao.getAll()
        val nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
        val normalizedLabel = if (label.isBlank()) "" else label
        val added = TimerPresetEntity(
            id = nextId,
            durationSeconds = durationSeconds,
            label = normalizedLabel,
            position = 0
        )
        dao.replaceAll((listOf(added) + current).reindexed())
        migrationPrefs.edit()
            .putBoolean(KEY_USER_CLEARED_ALL, false)
            .apply()
    }

    suspend fun removePreset(id: Long) {
        awaitMigration()
        val current = dao.getAll()
        val updated = current.filterNot { it.id == id }.reindexed()
        dao.replaceAll(updated)
        if (updated.isEmpty()) {
            migrationPrefs.edit()
                .putBoolean(KEY_USER_CLEARED_ALL, true)
                .apply()
        }
    }

    suspend fun updatePreset(id: Long, durationSeconds: Int, label: String) {
        if (durationSeconds <= 0) return
        awaitMigration()
        val current = dao.getAll()
        val normalizedLabel = if (label.isBlank()) "" else label
        dao.replaceAll(
            current.map { preset ->
                if (preset.id == id) {
                    preset.copy(durationSeconds = durationSeconds, label = normalizedLabel)
                } else {
                    preset
                }
            }.reindexed()
        )
    }

    suspend fun movePreset(fromIndex: Int, toIndex: Int) {
        awaitMigration()
        val current = dao.getAll().toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        dao.replaceAll(current.reindexed())
    }

    suspend fun reorderPresets(orderedIds: List<Long>) {
        awaitMigration()
        val current = dao.getAll()
        val byId = current.associateBy { it.id }.toMutableMap()
        val reordered = mutableListOf<TimerPresetEntity>()
        orderedIds.forEach { id ->
            byId.remove(id)?.let { reordered.add(it) }
        }
        reordered.addAll(current.filter { byId.containsKey(it.id) })
        dao.replaceAll(reordered.reindexed())
    }

    private suspend fun migrateLegacyIfNeeded() {
        val migrated = migrationPrefs.getBoolean(KEY_MIGRATED, false)
        if (!migrated) {
            if (dao.count() == 0) {
                val legacy = readLegacyPresets()
                dao.replaceAll(
                    legacy.mapIndexed { index, preset ->
                        TimerPresetEntity(
                            id = preset.id,
                            durationSeconds = preset.durationSeconds,
                            label = if (preset.label.isBlank()) "" else preset.label,
                            position = index
                        )
                    }
                )
            }
            migrationPrefs.edit()
                .putBoolean(KEY_MIGRATED, true)
                .apply()
        }
        seedDefaultsIfNeeded()
    }

    private suspend fun seedDefaultsIfNeeded() {
        // Recover sample presets when Room DB was recreated (destructive fallback),
        // but do not re-seed if user explicitly cleared all presets before.
        val userClearedAll = migrationPrefs.getBoolean(KEY_USER_CLEARED_ALL, false)
        if (dao.count() == 0 && !userClearedAll) {
            dao.replaceAll(defaultPresetEntities())
        }
    }

    private fun defaultPresetEntities(): List<TimerPresetEntity> {
        return defaultPresets().mapIndexed { index, preset ->
            TimerPresetEntity(
                id = preset.id,
                durationSeconds = preset.durationSeconds,
                label = if (preset.label.isBlank()) "" else preset.label,
                position = index
            )
        }
    }

    private suspend fun readLegacyPresets(): List<TimerPreset> {
        val preferences = context.legacyPresetDataStore.data.first()
        return readLegacyFromPreferences(preferences)
    }

    private fun readLegacyFromPreferences(preferences: Preferences): List<TimerPreset> {
        val raw = preferences[legacyPresetsKey]
        val initialized = preferences[legacyInitializedKey] ?: false
        return when {
            raw != null -> decode(raw)
            !initialized -> defaultPresets()
            else -> emptyList()
        }
    }

    private fun decode(raw: String): List<TimerPreset> {
        return raw.split(",")
            .mapNotNull { token ->
                val parts = token.split(":")
                val id = parts.getOrNull(0)?.toLongOrNull()
                val seconds = parts.getOrNull(1)?.toIntOrNull()
                if (id != null && seconds != null && seconds > 0) {
                    val label = when {
                        parts.size >= 3 -> runCatching {
                            val decoded = Base64.decode(parts[2], Base64.DEFAULT)
                            String(decoded, StandardCharsets.UTF_8)
                        }.getOrDefault("")
                        else -> ""
                    }
                    TimerPreset(id = id, durationSeconds = seconds, label = label)
                } else {
                    null
                }
            }
    }

    private fun List<TimerPresetEntity>.reindexed(): List<TimerPresetEntity> {
        return mapIndexed { index, item ->
            item.copy(position = index)
        }
    }

    private fun TimerPresetEntity.toModel(): TimerPreset {
        return TimerPreset(
            id = id,
            durationSeconds = durationSeconds,
            label = label
        )
    }

    companion object {
        private const val PREFS_NAME = "timer_preset_store"
        private const val KEY_MIGRATED = "room_migrated_v1"
        private const val KEY_USER_CLEARED_ALL = "user_cleared_all_presets_v1"
    }
}
