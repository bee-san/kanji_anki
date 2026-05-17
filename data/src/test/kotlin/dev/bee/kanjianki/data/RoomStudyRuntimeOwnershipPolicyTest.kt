package dev.bee.kanjianki.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudyRuntimeOwnershipPolicyTest {
    @Test
    fun disabledPolicyBlocksRoomRuntimeStudyOwnership() {
        val policy = RoomStudyRuntimeOwnershipPolicy.DISABLED

        assertFalse(policy.localStudyStateSafeForRoom())
        assertFalse(policy.canReadStudyRuntimeFromRoom())
        assertFalse(policy.canWriteReviewsToRoom())
    }

    @Test
    fun readAndWriteFlagsStillRequireMigrationOrDoubleWrite() {
        val unsafeEnabled = RoomStudyRuntimeOwnershipPolicy(
            roomStudyReadsEnabled = true,
            roomReviewWritesEnabled = true,
        )

        assertFalse(unsafeEnabled.localStudyStateSafeForRoom())
        assertFalse(unsafeEnabled.canReadStudyRuntimeFromRoom())
        assertFalse(unsafeEnabled.canWriteReviewsToRoom())
    }

    @Test
    fun completedMigrationCanEnableRoomRuntimeOwnership() {
        val policy = RoomStudyRuntimeOwnershipPolicy.ROOM_AUTHORITATIVE

        assertTrue(policy.localStudyStateSafeForRoom())
        assertTrue(policy.canReadStudyRuntimeFromRoom())
        assertTrue(policy.canWriteReviewsToRoom())
    }

    @Test
    fun doubleWriteCanEnableRoomRuntimeOwnershipBeforeCutover() {
        val policy = RoomStudyRuntimeOwnershipPolicy.LEGACY_DOUBLE_WRITE

        assertTrue(policy.localStudyStateSafeForRoom())
        assertTrue(policy.canReadStudyRuntimeFromRoom())
        assertTrue(policy.canWriteReviewsToRoom())
    }
}
