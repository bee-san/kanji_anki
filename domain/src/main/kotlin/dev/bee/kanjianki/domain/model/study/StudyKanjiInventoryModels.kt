package dev.bee.kanjianki.domain.model.study

data class StudyKanjiInventoryItem(
    val kanji: String,
    val primaryMeaning: String,
    val readings: String,
    val browserSearch: String,
    val sourceCount: Int,
    val exampleCount: Int,
    val suspended: Boolean,
    val lastSeenAtMillis: Long,
) {
    init {
        require(kanji.isNotBlank()) { "kanji must not be blank" }
        require(sourceCount >= 0) { "sourceCount must not be negative" }
        require(exampleCount >= 0) { "exampleCount must not be negative" }
        require(lastSeenAtMillis >= 0L) { "lastSeenAtMillis must not be negative" }
    }
}
