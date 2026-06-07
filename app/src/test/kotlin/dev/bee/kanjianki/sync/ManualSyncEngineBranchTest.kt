package dev.bee.kanjianki.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Constructor

class ManualSyncEngineBranchTest {
    @Test
    fun syncResultNormalizesNullAdaptiveSummary() {
        val constructor: Constructor<ManualSyncEngine.SyncResult> =
            ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
            )
        constructor.isAccessible = true

        val result = constructor.newInstance(
            true,
            false,
            3,
            2,
            "ok",
            null,
        )

        assertEquals("", result.adaptiveSummary)
    }

    @Test
    fun syncResultKeepsAdaptiveSummaryWhenPlannerReportsStatus() {
        val constructor: Constructor<ManualSyncEngine.SyncResult> =
            ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
            )
        constructor.isAccessible = true

        val result = constructor.newInstance(
            true,
            false,
            3,
            2,
            "ok",
            "3 due, 1 new",
        )

        assertEquals("3 due, 1 new", result.adaptiveSummary)
    }

    @Test
    fun syncResultCreateStoresPrecomputedStudyDetails() {
        val result = ManualSyncEngine.SyncResult.create(
            true,
            false,
            4,
            1,
            "ok",
            "3 due, 1 new",
            7,
            "Today's adaptive focus: 2 of 5 left",
        )

        assertEquals(7, result.studyReadyCount)
        assertEquals("Today's adaptive focus: 2 of 5 left", result.adaptiveFocusText)
    }
}
