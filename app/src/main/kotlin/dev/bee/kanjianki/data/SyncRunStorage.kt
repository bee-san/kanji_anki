package dev.bee.kanjianki.data

internal interface SyncRunStorage {
    fun insert(record: SyncRunRecord): Long

    fun updateRemovalMessage(syncId: Long, message: String)
}
