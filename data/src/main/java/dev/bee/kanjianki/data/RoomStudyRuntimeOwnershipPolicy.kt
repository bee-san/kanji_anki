package dev.bee.kanjianki.data

data class RoomStudyRuntimeOwnershipPolicy @JvmOverloads constructor(
    val existingInstallResetOrMigrationComplete: Boolean = false,
    val legacyDoubleWriteEnabled: Boolean = false,
    val roomStudyReadsEnabled: Boolean = false,
    val roomReviewWritesEnabled: Boolean = false,
) {
    fun canReadStudyRuntimeFromRoom(): Boolean =
        roomStudyReadsEnabled && localStudyStateSafeForRoom()

    fun canWriteReviewsToRoom(): Boolean =
        roomReviewWritesEnabled && localStudyStateSafeForRoom()

    fun localStudyStateSafeForRoom(): Boolean =
        existingInstallResetOrMigrationComplete || legacyDoubleWriteEnabled

    companion object {
        @JvmField
        val DISABLED: RoomStudyRuntimeOwnershipPolicy = RoomStudyRuntimeOwnershipPolicy()

        @JvmField
        val ROOM_AUTHORITATIVE: RoomStudyRuntimeOwnershipPolicy = RoomStudyRuntimeOwnershipPolicy(
            existingInstallResetOrMigrationComplete = true,
            roomStudyReadsEnabled = true,
            roomReviewWritesEnabled = true,
        )

        @JvmField
        val LEGACY_DOUBLE_WRITE: RoomStudyRuntimeOwnershipPolicy = RoomStudyRuntimeOwnershipPolicy(
            legacyDoubleWriteEnabled = true,
            roomStudyReadsEnabled = true,
            roomReviewWritesEnabled = true,
        )
    }
}
