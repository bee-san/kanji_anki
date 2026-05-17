package dev.bee.kanjianki.data

import android.content.Context
import androidx.room.Room

class KaniRoomDatabaseFactory(
    private val resetPolicy: KaniRoomDatabaseResetPolicy = KaniRoomDatabaseResetPolicy(),
) {
    fun create(context: Context): KaniRoomDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            KaniRoomDatabase::class.java,
            resetPolicy.roomDatabaseName,
        )
        if (resetPolicy.allowDestructiveRoomReset) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }
        return builder.build()
    }
}

data class KaniRoomDatabaseResetPolicy(
    val roomDatabaseName: String = KaniRoomDatabase.DATABASE_NAME,
    val legacyDatabaseNames: Set<String> = setOf(KaniRoomDatabase.LEGACY_DATABASE_NAME),
    val allowDestructiveRoomReset: Boolean = false,
) {
    init {
        require(roomDatabaseName.isNotBlank()) { "Room database name must be explicit." }
        require(roomDatabaseName !in legacyDatabaseNames) {
            "Room database must not reuse a legacy SQLiteOpenHelper database name."
        }
    }

    fun classify(databaseName: String): KaniRoomDatabaseDisposition = when (databaseName) {
        roomDatabaseName -> KaniRoomDatabaseDisposition.CURRENT_ROOM_DATABASE
        in legacyDatabaseNames -> KaniRoomDatabaseDisposition.LEGACY_LOCAL_STORE_DATABASE
        else -> KaniRoomDatabaseDisposition.UNKNOWN_DATABASE
    }

    companion object {
        @JvmField
        val CLEAN_REWRITE: KaniRoomDatabaseResetPolicy = KaniRoomDatabaseResetPolicy(
            allowDestructiveRoomReset = true,
        )
    }
}

enum class KaniRoomDatabaseDisposition {
    CURRENT_ROOM_DATABASE,
    LEGACY_LOCAL_STORE_DATABASE,
    UNKNOWN_DATABASE,
}
