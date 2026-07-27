package dev.bee.kanjianki.application

/**
 * Desktop startup contract. The profile lock remains held until presentation
 * exits, and every acquired resource is released in reverse startup order.
 */
class DesktopContainerLifecycle<C : KaniContainer>(
    private val stages: Stages<C>,
) {
    interface Stages<C : KaniContainer> {
        fun acquireProfileLock(): AutoCloseable

        fun applyStagedRestore()

        fun openData(): C

        fun startServices(container: C): AutoCloseable
    }

    fun <T> run(buildPresentation: (C) -> T): T {
        val acquired = ArrayList<AutoCloseable>(3)
        var primaryFailure: Throwable? = null
        try {
            acquired += stages.acquireProfileLock()
            stages.applyStagedRestore()
            val container = stages.openData()
            acquired += container
            acquired += stages.startServices(container)
            return buildPresentation(container)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            closeInReverse(acquired, primaryFailure)
        }
    }

    private fun closeInReverse(
        acquired: List<AutoCloseable>,
        primaryFailure: Throwable?,
    ) {
        var firstCloseFailure: Throwable? = null
        acquired.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (failure: Throwable) {
                val previous = primaryFailure ?: firstCloseFailure
                if (previous == null) {
                    firstCloseFailure = failure
                } else if (previous !== failure) {
                    previous.addSuppressed(failure)
                }
            }
        }
        if (primaryFailure == null) {
            firstCloseFailure?.let { throw it }
        }
    }
}
