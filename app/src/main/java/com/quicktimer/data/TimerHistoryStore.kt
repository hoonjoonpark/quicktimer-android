package com.quicktimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimerHistoryStore(
    private val dao: TimerHistoryDao
) {
    val historyFlow: Flow<List<TimerHistory>> = dao.observeAll().map { list ->
        list.map { entity ->
            TimerHistory(
                sessionId = entity.sessionId,
                label = entity.label,
                durationSeconds = entity.durationSeconds,
                startedAtEpochMs = entity.startedAtEpochMs,
                endedAtEpochMs = entity.endedAtEpochMs,
                status = TimerHistoryStatus.fromStorage(entity.status),
                extensionCount = entity.extensionCount,
                laps = decodeLaps(entity.lapsSerialized)
            )
        }
    }

    suspend fun nextSessionIdHint(): Long = (dao.maxSessionId() ?: 0L) + 1L

    suspend fun recordHistory(
        sessionId: Long,
        label: String,
        durationSeconds: Int,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        status: TimerHistoryStatus,
        extensionCount: Int,
        laps: List<String>
    ) {
        val existing = dao.findBySessionId(sessionId)
        val mergedLaps = when (existing) {
            null -> laps
            else -> decodeLaps(existing.lapsSerialized) + laps
        }.take(MAX_LAPS)

        dao.upsert(
            TimerHistoryEntity(
                sessionId = sessionId,
                label = if (label.isBlank()) "" else label,
                durationSeconds = durationSeconds,
                startedAtEpochMs = when {
                    existing?.startedAtEpochMs != null && existing.startedAtEpochMs > 0L -> existing.startedAtEpochMs
                    else -> startedAtEpochMs
                },
                endedAtEpochMs = maxOf(endedAtEpochMs, existing?.endedAtEpochMs ?: 0L),
                status = status.name,
                extensionCount = maxOf(extensionCount, existing?.extensionCount ?: 0),
                lapsSerialized = encodeLaps(mergedLaps)
            )
        )
    }

    suspend fun deleteHistory(sessionId: Long) {
        dao.deleteBySessionId(sessionId)
    }

    suspend fun clearAllHistory() {
        dao.deleteAll()
    }

    private fun encodeLaps(laps: List<String>): String {
        return laps.joinToString(LAP_SEPARATOR)
    }

    private fun decodeLaps(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(LAP_SEPARATOR).filter { it.isNotBlank() }
    }

    companion object {
        private const val LAP_SEPARATOR = "\u001F"
        private const val MAX_LAPS = 200
    }
}
