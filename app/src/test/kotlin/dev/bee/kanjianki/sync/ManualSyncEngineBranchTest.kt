package dev.bee.kanjianki.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSyncEngineBranchTest {
    @Test
    fun syncResultNormalizesNullAdaptiveSummary() {
        val result = newResult(null)

        assertEquals("", result.adaptiveSummary)
    }

    @Test
    fun syncResultKeepsAdaptiveSummaryWhenPlannerReportsStatus() {
        val result = newResult("3 due, 1 new")

        assertEquals("3 due, 1 new", result.adaptiveSummary)
    }

    private fun newResult(adaptiveSummary: String?): ManualSyncEngine.SyncResult {
        val constructor = ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            String::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            true,
            false,
            3,
            2,
            "ok",
            adaptiveSummary,
        )
    }
}
