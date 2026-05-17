package dev.bee.kanjianki.data.importing

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ImportRuleAuditDao {
    @Query("SELECT * FROM import_rule_audits WHERE sync_id = :syncId")
    suspend fun get(syncId: Long): ImportRuleAuditEntity?

    @Query("SELECT * FROM import_rule_audits ORDER BY sync_id DESC LIMIT 1")
    suspend fun latest(): ImportRuleAuditEntity?

    @Upsert
    suspend fun upsert(audit: ImportRuleAuditEntity)
}
