package dev.bee.kanjianki.widget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.LocalStoreWidgetDataPort
import dev.bee.kanjianki.data.WidgetDataPort
import dev.bee.kanjianki.core.KaniThemeChoice

internal sealed interface WidgetStoreRead<out T> {
    data class Ready<T>(val value: T) : WidgetStoreRead<T>
    data object NotSetUp : WidgetStoreRead<Nothing>
    data object Corrupt : WidgetStoreRead<Nothing>
}

/**
 * Opens only an already-initialized Kani database. Widget rendering never creates or
 * migrates it.
 *
 * [block] receives a [WidgetDataPort] rather than the store itself. That is the seam that
 * lets `:widget` be its own module: the port lives in `:data-api`, so a widget never names
 * an `:app` type, and it is read-only, so a widget cannot commit a review or start a sync
 * even by mistake.
 */
internal object WidgetLocalStoreReader {
    fun <T> read(context: Context, block: (WidgetDataPort) -> T): WidgetStoreRead<T> {
        val appContext = context.applicationContext
        val databaseFile = appContext.getDatabasePath(LocalStoreSchema.DB_NAME)
        if (!databaseFile.isFile) {
            return WidgetStoreRead.NotSetUp
        }
        if (!hasCurrentSchema(databaseFile.path)) {
            return WidgetStoreRead.Corrupt
        }
        return try {
            appContext.requireKaniContainer().openLocalStore().use { store ->
                WidgetStoreRead.Ready(block(LocalStoreWidgetDataPort(store)))
            }
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

internal fun WidgetDataPort.widgetThemeChoice(): KaniThemeChoice =
    KaniThemeChoice.fromStorageKey(themeStorageKey())
