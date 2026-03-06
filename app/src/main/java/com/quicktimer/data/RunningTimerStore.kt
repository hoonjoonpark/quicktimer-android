package com.quicktimer.data

class RunningTimerStore(
    private val dao: RunningTimerDao
) {
    suspend fun loadAll(): List<RunningTimerEntity> = dao.getAll()

    suspend fun syncAll(items: List<RunningTimerEntity>) {
        dao.syncAll(items)
    }

    suspend fun nextIdHint(): Int = (dao.maxId() ?: 0) + 1
}
