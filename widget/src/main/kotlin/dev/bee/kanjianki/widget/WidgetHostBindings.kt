package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.data.WidgetDataPort
import kotlin.coroutines.CoroutineContext

/**
 * What this module needs from the composition root, registered once at startup.
 *
 * A widget receiver is entered by the system, not constructed by the app, so it cannot be
 * given its dependencies at a call site — it has to find them. Before the module split it
 * found them by reaching into `:app`'s process container, which is exactly the dependency the
 * split removes. So the direction is inverted: `:app` registers, `:widget` reads.
 *
 * A registry rather than constructor injection because there is no constructor to inject
 * into, and a registry rather than a service locator over the whole container because these
 * two capabilities are all a widget legitimately needs — a lookup that could reach anything
 * would let the next change quietly re-couple the modules.
 */
object WidgetHostBindings {
    /**
     * Opens a read-only view of persisted state, or null before the app has registered one.
     *
     * Null is a real state, not a defensive check: a widget update can arrive before
     * `Application.onCreate` has finished — after a process kill the system may deliver an
     * `APPWIDGET_UPDATE` first — and the honest answer then is the not-set-up rendering
     * rather than a crash in a receiver the user cannot see.
     */
    @Volatile
    var openDataPort: ((Context) -> WidgetDataPort?)? = null

    /**
     * Resolves the dispatcher a refresh runs on, or null before the app has registered one.
     *
     * A function rather than the context itself, and that is load-bearing: registration
     * happens before the process container is built, so a binding that captured the
     * dispatcher eagerly would have to read a container that does not exist yet. Resolving on
     * use defers that read to the refresh, by which point startup has finished.
     *
     * The caller falls back to a plain background dispatcher when this is null or throws,
     * because a refresh has to run *somewhere* — skipping it would leave a stale widget on
     * the home screen.
     */
    @Volatile
    var refreshContext: (() -> CoroutineContext)? = null

    /**
     * Whether the database exists and is at the schema this build expects.
     *
     * Asked of the host rather than checked here, because the answer needs the database name
     * and version — and a widget that knew the schema version would have to be edited on
     * every migration, in a module that has no business tracking them. The host returns
     * false for "not created yet" and for "some other version", and the reader turns those
     * into the not-set-up and corrupt renderings respectively.
     */
    @Volatile
    var databaseIsCurrent: ((Context) -> DatabaseState)? = null

    /** Clears every binding. For tests, so one case cannot leak state into the next. */
    fun reset() {
        openDataPort = null
        refreshContext = null
        databaseIsCurrent = null
    }

    /**
     * What the host found at the database path.
     *
     * Three states rather than a Boolean because the widget renders them differently: a
     * missing database is a prompt to set Kani up, and a wrong-version one is a fault worth
     * showing as such rather than as "nothing here yet".
     */
    enum class DatabaseState { MISSING, WRONG_VERSION, CURRENT }
}
