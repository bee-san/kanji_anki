package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.data.WidgetDataPort
import dev.bee.kanjianki.core.KaniThemeChoice

/**
 * The three outcomes of opening the database, as a type rather than a nullable.
 *
 * Public because it is [WidgetLocalStoreReader.read]'s return type, and the distinction is
 * the point: "not set up yet" and "corrupt" render differently, so a caller that collapsed
 * them into null would lose the difference between an invitation to set Kani up and a fault
 * worth surfacing.
 */
sealed interface WidgetStoreRead<out T> {
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
object WidgetLocalStoreReader {
    fun <T> read(context: Context, block: (WidgetDataPort) -> T): WidgetStoreRead<T> {
        val appContext = context.applicationContext
        // The host owns the database name and schema version; this module only reacts to the
        // verdict. A widget that knew the version would need editing on every migration.
        when (WidgetHostBindings.databaseIsCurrent?.invoke(appContext)) {
            WidgetHostBindings.DatabaseState.MISSING, null -> return WidgetStoreRead.NotSetUp
            WidgetHostBindings.DatabaseState.WRONG_VERSION -> return WidgetStoreRead.Corrupt
            WidgetHostBindings.DatabaseState.CURRENT -> Unit
        }
        return try {
            // The port comes from `WidgetHostBindings`, which `:app` registers at startup.
            // A null one is not a failure to hide: an update can arrive before
            // `Application.onCreate` finished, and the not-set-up rendering is the honest
            // answer there.
            val open = WidgetHostBindings.openDataPort ?: return WidgetStoreRead.NotSetUp
            val port = open(appContext) ?: return WidgetStoreRead.NotSetUp
            WidgetStoreRead.Ready(block(port))
        } catch (_: Exception) {
            WidgetStoreRead.Corrupt
        }
    }
}

internal fun WidgetDataPort.widgetThemeChoice(): KaniThemeChoice =
    KaniThemeChoice.fromStorageKey(themeStorageKey())
