package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DurableStudyItemRetentionPolicyTest {
    @Test
    fun retainsPersistedKanjiOutsideSeedScope() {
        val updated = item("痛", "new-signature", 5_000L)
        val replaced = item("痛", "old-signature", 1_000L)
        val omitted = item("裂", "omitted-signature", 2_000L)

        val retained = DurableStudyItemRetentionPolicy.retainUnseeded(
            listOf(updated),
            listOf(replaced, omitted),
        )

        assertEquals(listOf("痛", "裂"), retained.map { it.kanji })
        assertSame(updated, retained[0])
        assertSame(omitted, retained[1])
    }

    @Test
    fun emptySeedScopeRetainsEveryPersistedItem() {
        val persisted = listOf(item("痛", "one", 1_000L), item("裂", "two", 2_000L))

        val retained = DurableStudyItemRetentionPolicy.retainUnseeded(emptyList(), persisted)

        assertEquals(persisted, retained)
        assertSame(persisted[0], retained[0])
        assertSame(persisted[1], retained[1])
    }

    private fun item(kanji: String, signature: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            StudyLadderRules.STATE_REVIEW,
            dueAtMillis,
            1.0,
            5.0,
            1,
            0,
            0,
            0,
            null,
            1L,
        ).copyBuilder().answerSignature(signature).build()
    }
}
