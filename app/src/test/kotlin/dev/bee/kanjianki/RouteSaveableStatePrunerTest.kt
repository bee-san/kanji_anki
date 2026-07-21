package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSaveableStatePrunerTest {
    @Test
    fun removesEachDepartedRouteWithoutRemovingRepublications() {
        val removed = mutableListOf<Any>()
        val pruner = RouteSaveableStatePruner()

        pruner.activate("browse-a", removed::add)
        pruner.activate("browse-a", removed::add)
        pruner.activate("study-1", removed::add)
        pruner.activate("study-2", removed::add)

        assertEquals(listOf("browse-a", "study-1"), removed)
    }
}
