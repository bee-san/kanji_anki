package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import java.util.concurrent.atomic.AtomicLong

class SqlProjectionInvalidation {
    private val homeGeneration = AtomicLong()

    fun homeVersion(): Long = homeGeneration.get()

    fun invalidateHome() {
        homeGeneration.incrementAndGet()
    }
}

/**
 * Driver-neutral Home, browse, detail, example, and local-suspension
 * persistence. Runtime Android composition remains on LocalStore until Goal
 * 184.
 */
class SqlHomeRepository(
    private val database: SqlDatabase,
    private val invalidation: SqlProjectionInvalidation = SqlProjectionInvalidation(),
) : HomeRepository {
    override suspend fun loadHome(nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot { SqlHomeData(this).loadHome(nowMillis) }
    }

    override suspend fun searchInventory(
        query: String,
        onlySimilarKanji: Boolean,
    ) = safeSqlStoreCall {
        database.readSnapshot {
            SqlHomeData(this).searchInventory(
                query,
                onlySimilarKanji,
                SqlHomeData.InventoryScope.ALL,
            )
        }
    }

    override suspend fun searchStudyInventory(
        query: String,
        onlySimilarKanji: Boolean,
        includeLocallySuspended: Boolean,
    ) = safeSqlStoreCall {
        database.readSnapshot {
            SqlHomeData(this).searchInventory(
                query,
                onlySimilarKanji,
                if (includeLocallySuspended) {
                    SqlHomeData.InventoryScope.STUDY_QUEUE_WITH_SUSPENDED
                } else {
                    SqlHomeData.InventoryScope.STUDY_QUEUE
                },
            )
        }
    }

    override suspend fun loadKanjiDetail(
        kanji: String,
        nowMillis: Long,
    ) = safeSqlStoreCall {
        database.readSnapshot { SqlHomeData(this).loadKanjiDetail(kanji, nowMillis) }
    }

    override suspend fun loadGameData() = safeSqlStoreCall {
        database.readSnapshot { SqlHomeData(this).loadGameData() }
    }

    override suspend fun loadNewCardSortPreviewData() = safeSqlStoreCall {
        val version = invalidation.homeVersion()
        database.readSnapshot { SqlHomeData(this).loadNewCardSortPreviewData(version) }
    }

    override suspend fun loadNewCardSortPreviewVersion() =
        safeSqlStoreCall { invalidation.homeVersion() }

    override suspend fun consumeDowngradeNotice() = safeSqlStoreCall {
        database.write {
            val stored = queryOneOrNull(
                "SELECT value FROM settings WHERE key = ? LIMIT 1",
                bind = { bindText(1, DOWNGRADED_FROM_VERSION_KEY) },
            ) { row -> row.textOrEmpty(0) }
            val version = stored?.toIntOrNull()
            if (version != null) {
                executeBound(
                    "DELETE FROM settings WHERE key = ?",
                    bind = { bindText(1, DOWNGRADED_FROM_VERSION_KEY) },
                )
            }
            version
        }
    }

    override suspend fun saveMnemonic(command: SaveMnemonicCommand) = safeSqlStoreCall {
        val key = TextUtil.normalizeSingleKanji(command.kanji)
        if (key.isNotEmpty()) {
            val note = command.note.trim()
            database.write {
                if (note.isEmpty()) {
                    executeBound(
                        "DELETE FROM kanji_mnemonic_notes WHERE kanji = ?",
                        bind = { bindText(1, key) },
                    )
                } else {
                    executeBound(
                        """
                        INSERT INTO kanji_mnemonic_notes(kanji, note, updated_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(kanji) DO UPDATE SET
                            note = excluded.note,
                            updated_at = excluded.updated_at
                        """.trimIndent(),
                    ) {
                        bindText(1, key)
                        bindText(2, note)
                        bindLong(3, command.updatedAtMillis)
                    }
                }
            }
        }
        Unit
    }

    override suspend fun setLocalSuspension(command: SetLocalSuspensionCommand) =
        safeSqlStoreCall {
            val kanji = command.kanji.filter(String::isNotEmpty).distinct()
            if (kanji.isNotEmpty()) {
                database.write {
                    kanji.forEach { value ->
                        if (command.suspended) {
                            executeBound(
                                """
                                INSERT INTO local_kanji_suspensions(kanji, suspended_at)
                                VALUES (?, ?)
                                ON CONFLICT(kanji) DO UPDATE SET
                                    suspended_at = excluded.suspended_at
                                """.trimIndent(),
                            ) {
                                bindText(1, value)
                                bindLong(2, command.updatedAtMillis)
                            }
                            executeBound(
                                "DELETE FROM learning_repeats WHERE kanji = ?",
                                bind = { bindText(1, value) },
                            )
                        } else {
                            executeBound(
                                "DELETE FROM local_kanji_suspensions WHERE kanji = ?",
                                bind = { bindText(1, value) },
                            )
                        }
                    }
                    markStatsDirty()
                }
                invalidation.invalidateHome()
            }
            Unit
        }

    private fun SqlSession.markStatsDirty() {
        executeBound(
            """
            INSERT INTO stats_cache_state(key, value)
            VALUES (?, 2)
            ON CONFLICT(key) DO UPDATE SET value = value + 1
            """.trimIndent(),
        ) {
            bindText(1, STATS_SOURCE_VERSION_KEY)
        }
    }

    private companion object {
        const val DOWNGRADED_FROM_VERSION_KEY = "downgraded_from_version"
        const val STATS_SOURCE_VERSION_KEY = "stats_source_version"
    }
}
