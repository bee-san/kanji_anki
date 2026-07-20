package dev.bee.kanjianki.widget

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.core.KaniThemeChoice

internal sealed interface WidgetStoreRead<out T> {
    data class Ready<T>(val value: T) : WidgetStoreRead<T>
    data object NotSetUp : WidgetStoreRead<Nothing>
    data object Corrupt : WidgetStoreRead<Nothing>
}

/** Opens only an already-initialized Kani database. Widget rendering never creates or migrates it. */
internal object WidgetLocalStoreReader {
    fun <T> read(context: Context, block: (LocalStore) -> T): WidgetStoreRead<T> {
        val appContext = context.applicationContext
        val databaseFile = appContext.getDatabasePath(LocalStoreSchema.DB_NAME)
        if (!databaseFile.isFile) {
            return WidgetStoreRead.NotSetUp
        }
        if (!hasCurrentSchema(databaseFile.path)) {
            return WidgetStoreRead.Corrupt
        }
        return try {
            AppLocalStoreFactory.create(appContext).use { store -> WidgetStoreRead.Ready(block(store)) }
        } catch (_: Exception) {
            WidgetStoreRead.Corrupt
        }
    }

    private fun hasCurrentSchema(path: String): Boolean = try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.version == LocalStoreSchema.DB_VERSION
        }
    } catch (_: Exception) {
        false
    }
}

internal fun LocalStore.widgetThemeChoice(): KaniThemeChoice =
    KaniThemeChoice.fromStorageKey(getStringSetting(KaniThemeChoice.SETTING_KEY, null))
