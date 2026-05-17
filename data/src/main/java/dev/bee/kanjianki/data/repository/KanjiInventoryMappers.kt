package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.inventory.KanjiInventoryEntity
import dev.bee.kanjianki.domain.sync.SyncKanjiInventoryRecord

internal fun SyncKanjiInventoryRecord.toEntity(
    previous: KanjiInventoryEntity?,
    nowMillis: Long,
): KanjiInventoryEntity = KanjiInventoryEntity(
    kanji = kanji,
    primaryMeaning = primaryMeaning.ifBlank { previous?.primaryMeaning.orEmpty() },
    readings = readings.ifBlank { previous?.readings.orEmpty() },
    browserSearch = browserSearch.ifBlank { previous?.browserSearch.orEmpty() },
    searchText = mergedSearchText(previous),
    sourceCount = maxOf(sourceCount, previous?.sourceCount ?: 0),
    exampleCount = maxOf(exampleCount, previous?.exampleCount ?: 0),
    firstSeenAt = previous?.firstSeenAt ?: nowMillis,
    lastSeenAt = nowMillis,
)

private fun SyncKanjiInventoryRecord.mergedSearchText(previous: KanjiInventoryEntity?): String =
    listOf(
        searchText,
        previous?.primaryMeaning.orEmpty(),
        previous?.readings.orEmpty(),
        previous?.browserSearch.orEmpty(),
    )
        .flatMap { it.split(Regex("\\s+")) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
