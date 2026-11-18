package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniMemoryModel

/**
 * Where persistence-side simulations (the completion forecast) get a memory
 * model that writes only to a scratch copy of the persisted state. The
 * memory-model runtime registers the factory when the process starts, so the
 * data layer does not depend on it; with no factory (or no ready state) there
 * is no simulation.
 */
internal object MemoryModelSimulations {
    @Volatile
    var factory: ((LocalStore) -> KaniMemoryModel?)? = null

    fun simulationModel(store: LocalStore): KaniMemoryModel? = factory?.invoke(store)
}
